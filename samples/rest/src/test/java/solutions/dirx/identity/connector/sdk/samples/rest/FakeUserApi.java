/*
 * Copyright 2026 Atos Secure Digital GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package solutions.dirx.identity.connector.sdk.samples.rest;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import solutions.dirx.identity.connector.sdk.rest.Json;

/** In-memory implementation of the user API described in {@link UserApiConnector}. */
final class FakeUserApi implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, ObjectNode> users = new LinkedHashMap<>();
    final List<String> requests = new ArrayList<>();

    FakeUserApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/users", this::handle);
        server.start();
    }

    URI baseUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/");
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getRawPath().substring("/api/users".length());
            String id = path.length() > 1 ? URLDecoder.decode(path.substring(1), StandardCharsets.UTF_8) : null;
            String method = exchange.getRequestMethod();
            synchronized (this) {
                requests.add(method + " " + exchange.getRequestURI());
                if (id == null) {
                    switch (method) {
                        case "GET" -> list(exchange);
                        case "POST" -> create(exchange);
                        default -> send(exchange, 405, null);
                    }
                    return;
                }
                ObjectNode user = users.get(id);
                if (user == null) {
                    send(exchange, 404, error("user " + id + " not found"));
                    return;
                }
                switch (method) {
                    case "GET" -> send(exchange, 200, user);
                    case "PUT" -> {
                        ObjectNode body = (ObjectNode) Json.mapper().readTree(exchange.getRequestBody());
                        body.put("id", id);
                        body.set("created", user.get("created"));
                        users.put(id, body);
                        send(exchange, 200, body);
                    }
                    case "DELETE" -> {
                        users.remove(id);
                        send(exchange, 204, null);
                    }
                    default -> send(exchange, 405, null);
                }
            }
        }
    }

    private void list(HttpExchange exchange) throws IOException {
        Map<String, String> query = new LinkedHashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw != null) {
            for (String pair : raw.split("&")) {
                String[] kv = pair.split("=", 2);
                query.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
            }
        }
        int offset = Integer.parseInt(query.getOrDefault("offset", "0"));
        int limit = Integer.parseInt(query.getOrDefault("limit", "50"));
        List<ObjectNode> matches = users.values().stream()
                .filter(u -> query.entrySet().stream()
                        .filter(e -> !e.getKey().equals("offset") && !e.getKey().equals("limit"))
                        .allMatch(e -> e.getValue().equalsIgnoreCase(u.path(e.getKey()).asString(""))))
                .toList();
        ObjectNode result = Json.object();
        ArrayNode items = result.putArray("items");
        matches.stream().skip(offset).limit(limit).forEach(items::add);
        result.put("total", matches.size());
        send(exchange, 200, result);
    }

    private void create(HttpExchange exchange) throws IOException {
        ObjectNode body = (ObjectNode) Json.mapper().readTree(exchange.getRequestBody());
        String id = body.path("id").asString("");
        if (id.isEmpty()) {
            send(exchange, 400, error("id is required"));
        } else if (users.keySet().stream().anyMatch(id::equalsIgnoreCase)) {
            send(exchange, 409, error("user " + id + " exists"));
        } else {
            body.put("created", Instant.now().toString());
            users.put(id, body);
            send(exchange, 201, body);
        }
    }

    private static JsonNode error(String message) {
        return Json.object().put("message", message);
    }

    private static void send(HttpExchange exchange, int status, JsonNode body) throws IOException {
        byte[] bytes = body == null ? new byte[0] : Json.mapper().writeValueAsBytes(body);
        if (body != null) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
        }
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }
}
