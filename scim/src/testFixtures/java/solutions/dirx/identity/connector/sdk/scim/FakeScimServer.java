/*
 * Copyright 2026 Atos Secure Digital GmbH
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package solutions.dirx.identity.connector.sdk.scim;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import solutions.dirx.identity.connector.sdk.rest.Json;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** A minimal in-memory SCIM service provider for tests (Users only, simple filters). */
public final class FakeScimServer implements AutoCloseable {

    private static final Pattern EQ = Pattern.compile("^([\\w.]+) eq \"(.*)\"$");
    private static final Pattern VALUE_FILTER = Pattern.compile("^(\\w+)\\[value eq \"(.*)\"]$");

    /**
     * A recorded HTTP request.
     *
     * @param method the HTTP method
     * @param uri    the request URI
     * @param body   the request body
     */
    public record Recorded(String method, String uri, String body) {
    }

    private final HttpServer server;
    private final Map<String, ObjectNode> users = Collections.synchronizedMap(new LinkedHashMap<>());
    /** The requests received so far. */
    public final List<Recorded> requests = Collections.synchronizedList(new ArrayList<>());

    /**
     * Starts the server on an ephemeral port.
     *
     * @throws IOException if the server cannot be started
     */
    public FakeScimServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/scim/", this::handle);
        server.start();
    }

    /** {@return the SCIM base URI, e.g. {@code http://127.0.0.1:1234/scim/v2}} */
    public URI baseUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/scim/v2");
    }

    /** {@return the stored users by id} */
    public Map<String, ObjectNode> users() {
        return users;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Recorded(ex.getRequestMethod(), ex.getRequestURI().toString(), body));
        String path = ex.getRequestURI().getPath().substring("/scim/v2/".length());
        String[] segments = path.split("/");
        try {
            if (path.equals("ServiceProviderConfig")) {
                reply(ex, 200, Json.object().put("patch", true));
            } else if (!segments[0].equals("Users")) {
                error(ex, 404, null, "unknown endpoint");
            } else if (segments.length == 1) {
                switch (ex.getRequestMethod()) {
                    case "GET" -> list(ex);
                    case "POST" -> create(ex, (ObjectNode) Json.mapper().readTree(body));
                    default -> error(ex, 405, null, "method not allowed");
                }
            } else {
                String id = segments[1];
                ObjectNode user = users.get(id);
                if (user == null) {
                    error(ex, 404, null, "Resource " + id + " not found");
                    return;
                }
                switch (ex.getRequestMethod()) {
                    case "GET" -> reply(ex, 200, user);
                    case "DELETE" -> {
                        users.remove(id);
                        reply(ex, 204, null);
                    }
                    case "PATCH" -> patch(ex, user, Json.mapper().readTree(body));
                    case "PUT" -> {
                        ObjectNode replacement = (ObjectNode) Json.mapper().readTree(body);
                        replacement.put("id", id);
                        users.put(id, replacement);
                        reply(ex, 200, replacement);
                    }
                    default -> error(ex, 405, null, "method not allowed");
                }
            }
        } catch (RuntimeException e) {
            error(ex, 500, null, e.toString());
        }
    }

    private void create(HttpExchange ex, ObjectNode user) throws IOException {
        String userName = user.path("userName").asString("");
        if (userName.isEmpty()) {
            error(ex, 400, "invalidValue", "userName is required");
            return;
        }
        synchronized (users) {
            boolean exists = users.values().stream()
                    .anyMatch(u -> u.path("userName").asString("").equalsIgnoreCase(userName));
            if (exists) {
                error(ex, 409, "uniqueness", "userName " + userName + " already exists");
                return;
            }
            String id = UUID.randomUUID().toString();
            user.put("id", id);
            user.putObject("meta").put("resourceType", "User");
            users.put(id, user);
        }
        reply(ex, 201, user);
    }

    private void list(HttpExchange ex) throws IOException {
        Map<String, String> query = query(ex.getRequestURI().getRawQuery());
        List<ObjectNode> hits;
        synchronized (users) {
            hits = new ArrayList<>(users.values());
        }
        String filter = query.get("filter");
        if (filter != null) {
            Matcher m = EQ.matcher(filter);
            if (!m.matches()) {
                error(ex, 400, "invalidFilter", "unsupported filter " + filter);
                return;
            }
            hits = hits.stream().filter(u -> value(u, m.group(1)).equalsIgnoreCase(m.group(2))).toList();
        }
        int start = Integer.parseInt(query.getOrDefault("startIndex", "1"));
        int count = Integer.parseInt(query.getOrDefault("count", "100"));
        List<ObjectNode> page = hits.subList(Math.min(start - 1, hits.size()), Math.min(start - 1 + count, hits.size()));
        ObjectNode response = Json.object();
        response.putArray("schemas").add("urn:ietf:params:scim:api:messages:2.0:ListResponse");
        response.put("totalResults", hits.size());
        response.put("startIndex", start);
        response.put("itemsPerPage", page.size());
        ArrayNode resources = response.putArray("Resources");
        page.forEach(resources::add);
        reply(ex, 200, response);
    }

    private void patch(HttpExchange ex, ObjectNode user, JsonNode body) throws IOException {
        for (JsonNode op : body.path("Operations").values()) {
            String path = op.path("path").asString();
            JsonNode value = op.path("value");
            switch (op.path("op").asString()) {
                case "replace" -> set(user, path, value);
                case "add" -> {
                    JsonNode existing = user.path(path);
                    if (existing.isArray() && value.isArray()) {
                        value.values().forEach(((ArrayNode) existing)::add);
                    } else {
                        set(user, path, value);
                    }
                }
                case "remove" -> {
                    Matcher m = VALUE_FILTER.matcher(path);
                    if (m.matches()) {
                        JsonNode array = user.path(m.group(1));
                        for (int i = array.size() - 1; i >= 0; i--) {
                            JsonNode element = array.get(i);
                            String v = element.isObject() ? element.path("value").asString("") : element.asString("");
                            if (v.equals(m.group(2))) {
                                ((ArrayNode) array).remove(i);
                            }
                        }
                    } else {
                        remove(user, path);
                    }
                }
                default -> {
                    error(ex, 400, "invalidSyntax", "bad op");
                    return;
                }
            }
        }
        reply(ex, 200, user);
    }

    private static String value(JsonNode node, String path) {
        int split = splitIndex(path);
        JsonNode current = split < 0 ? node.path(path)
                : node.path(path.substring(0, split)).path(path.substring(split + 1));
        return current.asString("");
    }

    /**
     * Returns the position separating a parent from a sub-attribute: the dot of
     * {@code name.givenName}, or the last colon of an extension path such as
     * {@code urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:employeeNumber}.
     */
    private static int splitIndex(String path) {
        return path.startsWith("urn:") ? path.lastIndexOf(':') : path.indexOf('.');
    }

    private static void set(ObjectNode node, String path, JsonNode value) {
        int split = splitIndex(path);
        if (split < 0) {
            node.set(path, value);
        } else {
            JsonNode child = node.path(path.substring(0, split));
            ObjectNode obj = child.isObject() ? (ObjectNode) child : node.putObject(path.substring(0, split));
            obj.set(path.substring(split + 1), value);
        }
    }

    private static void remove(ObjectNode node, String path) {
        int split = splitIndex(path);
        if (split < 0) {
            node.remove(path);
        } else if (node.path(path.substring(0, split)).isObject()) {
            ((ObjectNode) node.path(path.substring(0, split))).remove(path.substring(split + 1));
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> result = new HashMap<>();
        if (raw != null) {
            for (String pair : raw.split("&")) {
                int eq = pair.indexOf('=');
                result.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    private static void error(HttpExchange ex, int status, String scimType, String detail) throws IOException {
        ObjectNode error = Json.object();
        error.putArray("schemas").add("urn:ietf:params:scim:api:messages:2.0:Error");
        error.put("status", String.valueOf(status));
        if (scimType != null) {
            error.put("scimType", scimType);
        }
        error.put("detail", detail);
        reply(ex, status, error);
    }

    private static void reply(HttpExchange ex, int status, JsonNode body) throws IOException {
        byte[] bytes = body == null ? new byte[0] : body.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/scim+json");
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            ex.getResponseBody().write(bytes);
        }
        ex.close();
    }
}
