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

package solutions.dirx.identity.connector.sdk.samples.rest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import solutions.dirx.identity.connector.sdk.api.Connector;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.CreateOp;
import solutions.dirx.identity.connector.sdk.api.CreateRequest;
import solutions.dirx.identity.connector.sdk.api.CreateResult;
import solutions.dirx.identity.connector.sdk.api.DeleteOp;
import solutions.dirx.identity.connector.sdk.api.DeleteRequest;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.ResultHandler;
import solutions.dirx.identity.connector.sdk.api.SchemaOp;
import solutions.dirx.identity.connector.sdk.api.SearchOp;
import solutions.dirx.identity.connector.sdk.api.SearchRequest;
import solutions.dirx.identity.connector.sdk.api.SearchResult;
import solutions.dirx.identity.connector.sdk.api.TestOp;
import solutions.dirx.identity.connector.sdk.api.UpdateOp;
import solutions.dirx.identity.connector.sdk.api.UpdateRequest;
import solutions.dirx.identity.connector.sdk.api.UpdateResult;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorContext;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.ConnectorObject;
import solutions.dirx.identity.connector.sdk.api.model.FilterEvaluator;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.api.model.Schema;
import solutions.dirx.identity.connector.sdk.api.model.Schema.AttributeInfo;
import solutions.dirx.identity.connector.sdk.api.model.Schema.AttributeType;
import solutions.dirx.identity.connector.sdk.api.model.SearchFilter;
import solutions.dirx.identity.connector.sdk.api.model.SearchScope;
import solutions.dirx.identity.connector.sdk.api.spi.ConnectorInfo;
import solutions.dirx.identity.connector.sdk.rest.Json;
import solutions.dirx.identity.connector.sdk.rest.PageCookies;
import solutions.dirx.identity.connector.sdk.rest.RestClient;
import solutions.dirx.identity.connector.sdk.rest.RestRequest;

/**
 * Connector for a fictional user API with these endpoints:
 *
 * <pre>
 * GET    /users?offset=0&amp;limit=50[&amp;&lt;attribute&gt;=&lt;value&gt;]  -&gt; {"items": [...], "total": n}
 * GET    /users/{id}
 * POST   /users            (409 if the id exists)
 * PUT    /users/{id}       (full replacement)
 * DELETE /users/{id}
 * </pre>
 *
 * <p>A user looks like {@code {"id": "jdoe", "email": "...", "name": {"given": "...",
 * "family": "..."}, "roles": ["..."], "active": true}}. Nested properties become dotted
 * attribute names such as {@code name.given}.
 *
 * <p>The base URL and credentials come from the standard connection settings; see {@link
 * RestClient#fromConfiguration} for the supported authentication properties.
 */
@ConnectorInfo(name = "sample-rest", displayName = "User REST API (sample)",
        description = "Sample connector for a simple JSON user API", objectTypes = "User")
public class UserApiConnector implements Connector, TestOp, SchemaOp, CreateOp, SearchOp, UpdateOp, DeleteOp {

    /** The only object type. */
    public static final String USER = "User";

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final Set<String> MULTI_VALUED = Set.of("roles");
    private static final Set<String> SERVER_FILTERABLE = Set.of("email", "active");

    private RestClient client;

    /** Creates an uninitialized connector. */
    public UserApiConnector() {
        // initialized by init()
    }

    @Override
    public void init(ConnectorContext context) throws ConnectorException {
        client = RestClient.fromConfiguration(context.configuration()).build();
    }

    @Override
    public void close() {
        client = null;
    }

    @Override
    public void test() throws ConnectorException {
        client.send(RestRequest.get("users").query("limit", 1).build());
    }

    @Override
    public Schema schema() {
        return Schema.of(Schema.ObjectClassInfo.of(USER,
                AttributeInfo.of("email", AttributeType.STRING),
                AttributeInfo.of("name.given", AttributeType.STRING),
                AttributeInfo.of("name.family", AttributeType.STRING),
                AttributeInfo.of("roles", AttributeType.STRING).asMultiValued(),
                AttributeInfo.of("active", AttributeType.BOOLEAN),
                AttributeInfo.of("created", AttributeType.STRING).asReadOnly()));
    }

    @Override
    public CreateResult create(CreateRequest request) throws ConnectorException {
        checkType(request.objectType());
        if (request.identifier() == null) {
            throw new ConnectorException(ErrorCode.SCHEMA_VIOLATION, "the user id is required");
        }
        ObjectNode body = Json.toObject(request.attributes(), MULTI_VALUED::contains);
        body.put("id", request.identifier().value());
        JsonNode created = client.postJson("users", body);
        return new CreateResult(Identifier.of(created.path("id").asString(request.identifier().value())),
                Json.toAttributes(created, "id"));
    }

    @Override
    public SearchResult search(SearchRequest request, ResultHandler handler) throws ConnectorException {
        checkType(request.objectType());
        if (request.base() != null && request.scope() == SearchScope.OBJECT) {
            return read(request, handler);
        }
        Optional<SearchFilter.Equals> serverFilter = serverFilter(request.filter());
        int limit = request.pageSize() > 0 ? request.pageSize() : DEFAULT_PAGE_SIZE;
        long offset = PageCookies.offset(request.pageCookie(), 0);
        while (true) {
            RestRequest.Builder get = RestRequest.get("users").query("offset", offset).query("limit", limit);
            serverFilter.ifPresent(f -> get.query(f.attribute(), f.value()));
            JsonNode page = client.send(get.build()).json();
            List<JsonNode> items = new ArrayList<>(page.path("items").values());
            for (JsonNode item : items) {
                ConnectorObject object = toObject(request, item);
                // the server only applies the simple equality filter; evaluate the full filter here
                if (request.filter() != null && !FilterEvaluator.matches(request.filter(), object)) {
                    continue;
                }
                if (!handler.handle(object)) {
                    return SearchResult.done();
                }
            }
            offset += items.size();
            boolean more = !items.isEmpty() && offset < page.path("total").asLong(0);
            if (!more) {
                return SearchResult.done();
            }
            if (request.pageSize() > 0) {
                return SearchResult.nextPage(PageCookies.ofOffset(offset));
            }
        }
    }

    @Override
    public UpdateResult update(UpdateRequest request) throws ConnectorException {
        checkType(request.objectType());
        for (Modification mod : request.modifications()) {
            if (mod.hasName("id") || mod.hasName("created")) {
                throw new ConnectorException(ErrorCode.SCHEMA_VIOLATION, mod.attributeName() + " cannot be changed");
            }
        }
        String path = path(request.identifier());
        if (!(client.getJson(path) instanceof ObjectNode updated)) {
            throw new ConnectorException(ErrorCode.OTHER, "unexpected response for " + path);
        }
        Json.apply(updated, request.modifications(), MULTI_VALUED::contains);
        client.putJson(path, updated);
        return UpdateResult.unchanged();
    }

    @Override
    public void delete(DeleteRequest request) throws ConnectorException {
        checkType(request.objectType());
        client.delete(path(request.identifier()));
    }

    private SearchResult read(SearchRequest request, ResultHandler handler) throws ConnectorException {
        var response = client.send(RestRequest.get(path(request.base())).accept(200, 404).build());
        if (response.statusCode() == 200) {
            ConnectorObject object = toObject(request, response.json());
            if (request.filter() == null || FilterEvaluator.matches(request.filter(), object)) {
                handler.handle(object);
            }
        }
        return SearchResult.done();
    }

    private static ConnectorObject toObject(SearchRequest request, JsonNode item) {
        ConnectorObject.Builder builder = ConnectorObject.builder(USER, Identifier.of(item.path("id").asString()));
        for (Attribute attribute : Json.toAttributes(item, "id")) {
            if (request.isRequested(attribute.name())) {
                builder.attribute(attribute);
            }
        }
        return builder.build();
    }

    /** Pushes a top-level equality filter to the server; everything else is filtered locally. */
    private static Optional<SearchFilter.Equals> serverFilter(SearchFilter filter) {
        if (filter instanceof SearchFilter.Equals eq && SERVER_FILTERABLE.contains(eq.attribute())) {
            return Optional.of(eq);
        }
        if (filter instanceof SearchFilter.And and) {
            return and.filters().stream().map(UserApiConnector::serverFilter).flatMap(Optional::stream).findFirst();
        }
        return Optional.empty();
    }

    private static String path(Identifier id) {
        return "users/" + URLEncoder.encode(id.value(), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void checkType(String type) throws ConnectorException {
        if (!USER.equalsIgnoreCase(type)) {
            throw new ConnectorException(ErrorCode.INVALID_REQUEST, "unsupported object type " + type);
        }
    }
}
