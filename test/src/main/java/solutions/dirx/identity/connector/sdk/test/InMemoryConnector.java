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

package solutions.dirx.identity.connector.sdk.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
import solutions.dirx.identity.connector.sdk.api.model.SearchScope;
import solutions.dirx.identity.connector.sdk.api.spi.ConnectorInfo;

/**
 * A complete connector that keeps its objects in memory.
 *
 * <p>It serves as a reference implementation of all operations, as a stand-in target in
 * tests of hosts and workflows, and as a starting point for new connectors.
 *
 * <p>Behavior:
 * <ul>
 *   <li>Objects are keyed by object type and identifier value (case-insensitive).</li>
 *   <li>If a create request has no identifier, a random UUID is assigned.</li>
 *   <li>The configuration property {@value #PROP_STORE} names a shared store. Instances
 *       with the same store name see the same objects, which simulates one target system
 *       accessed by several threads. Without it, each instance has a private store.</li>
 *   <li>Searches evaluate filters with {@link FilterEvaluator}, return objects sorted by
 *       identifier, and support paging with a numeric offset as cookie.</li>
 *   <li>{@link Schema} is empty: any object type and attribute is accepted.</li>
 * </ul>
 */
@ConnectorInfo(name = "in-memory", version = "1.0", displayName = "In-memory connector",
        description = "Keeps objects in memory. For tests and as a reference implementation.")
public class InMemoryConnector implements Connector, CreateOp, UpdateOp, DeleteOp, SearchOp, SchemaOp, TestOp {

    /** Configuration property naming a store shared between instances. */
    public static final String PROP_STORE = "store";

    private static final Map<String, Map<String, ConnectorObject>> SHARED = new ConcurrentHashMap<>();

    private Map<String, ConnectorObject> objects;

    /** Creates an uninitialized connector. Hosts instantiate connectors through this constructor. */
    public InMemoryConnector() {
    }

    /**
     * Removes all objects from a shared store.
     *
     * @param storeName the store name
     */
    public static void clearStore(String storeName) {
        Map<String, ConnectorObject> store = SHARED.get(storeName);
        if (store != null) {
            store.clear();
        }
    }

    @Override
    public void init(ConnectorContext context) {
        Optional<String> store = context.configuration().get(PROP_STORE);
        objects = store.isPresent()
                ? SHARED.computeIfAbsent(store.get(), k -> new ConcurrentHashMap<>())
                : new ConcurrentHashMap<>();
        context.logger(InMemoryConnector.class).log(System.Logger.Level.DEBUG,
                "Initialized in-memory connector {0}", context.instanceName());
    }

    @Override
    public void close() {
        objects = null;
    }

    @Override
    public void test() throws ConnectorException {
        state();
    }

    @Override
    public Schema schema() {
        return Schema.of();
    }

    @Override
    public CreateResult create(CreateRequest request) throws ConnectorException {
        Map<String, ConnectorObject> store = state();
        Identifier id = request.identifier() != null ? request.identifier()
                : Identifier.of(UUID.randomUUID().toString());
        ConnectorObject.Builder builder = ConnectorObject.builder(request.objectType(), id);
        request.attributes().forEach(builder::attribute);
        if (store.putIfAbsent(key(request.objectType(), id), builder.build()) != null) {
            throw new ConnectorException(ErrorCode.ALREADY_EXISTS, "Object already exists: " + id.value());
        }
        return CreateResult.of(id);
    }

    @Override
    public UpdateResult update(UpdateRequest request) throws ConnectorException {
        Map<String, ConnectorObject> store = state();
        String key = key(request.objectType(), request.identifier());
        synchronized (store) {
            ConnectorObject current = store.get(key);
            if (current == null) {
                throw notFound(request.identifier());
            }
            List<Attribute> attributes = new ArrayList<>(current.attributes());
            for (Modification mod : request.modifications()) {
                apply(attributes, mod);
            }
            store.put(key, new ConnectorObject(current.objectType(), current.identifier(), attributes));
        }
        return UpdateResult.unchanged();
    }

    @Override
    public void delete(DeleteRequest request) throws ConnectorException {
        if (state().remove(key(request.objectType(), request.identifier())) == null) {
            throw notFound(request.identifier());
        }
    }

    @Override
    public SearchResult search(SearchRequest request, ResultHandler handler) throws ConnectorException {
        Map<String, ConnectorObject> store = state();
        List<ConnectorObject> hits;
        if (request.scope() == SearchScope.OBJECT && request.base() != null) {
            ConnectorObject object = store.get(key(request.objectType(), request.base()));
            hits = object != null && FilterEvaluator.matches(request.filter(), object)
                    ? List.of(object) : List.of();
        } else {
            hits = store.values().stream()
                    .filter(o -> o.objectType().equalsIgnoreCase(request.objectType()))
                    .filter(o -> FilterEvaluator.matches(request.filter(), o))
                    .sorted(Comparator.comparing(o -> o.identifier().value().toLowerCase(Locale.ROOT)))
                    .toList();
        }
        int offset = parseCookie(request.pageCookie());
        int end = request.pageSize() > 0 ? Math.min(hits.size(), offset + request.pageSize()) : hits.size();
        for (int i = offset; i < end; i++) {
            if (!handler.handle(project(hits.get(i), request))) {
                return SearchResult.done();
            }
        }
        return end < hits.size() ? SearchResult.nextPage(Integer.toString(end)) : SearchResult.done();
    }

    /**
     * Returns a snapshot of all stored objects. Intended for assertions in tests.
     *
     * @return the objects
     * @throws ConnectorException if the connector is not initialized
     */
    public List<ConnectorObject> objects() throws ConnectorException {
        return Collections.unmodifiableList(new ArrayList<>(state().values()));
    }

    private Map<String, ConnectorObject> state() throws ConnectorException {
        Map<String, ConnectorObject> store = objects;
        if (store == null) {
            throw new ConnectorException(ErrorCode.CONFIGURATION, "Connector is not initialized");
        }
        return store;
    }

    private static String key(String objectType, Identifier id) throws ConnectorException {
        if (objectType == null || id == null) {
            throw new ConnectorException(ErrorCode.INVALID_REQUEST, "Object type and identifier are required");
        }
        return objectType.toLowerCase(Locale.ROOT) + '\u0000' + id.value().toLowerCase(Locale.ROOT);
    }

    private static ConnectorException notFound(Identifier id) {
        return new ConnectorException(ErrorCode.NOT_FOUND, "No such object: " + id.value());
    }

    private static int parseCookie(String cookie) throws ConnectorException {
        if (cookie == null || cookie.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(cookie);
        } catch (NumberFormatException e) {
            throw new ConnectorException(ErrorCode.INVALID_REQUEST, "Invalid page cookie: " + cookie, e);
        }
    }

    private static void apply(List<Attribute> attributes, Modification mod) throws ConnectorException {
        int idx = indexOf(attributes, mod.attributeName());
        List<Object> current = idx < 0 ? new ArrayList<>() : new ArrayList<>(attributes.get(idx).values());
        switch (mod.operation()) {
            case REPLACE -> current = new ArrayList<>(mod.values());
            case ADD -> {
                for (Object v : mod.values()) {
                    if (current.contains(v)) {
                        throw new ConnectorException(ErrorCode.ATTRIBUTE_OR_VALUE_EXISTS,
                                "Value exists in " + mod.attributeName());
                    }
                    current.add(v);
                }
            }
            case DELETE -> {
                if (idx < 0) {
                    throw new ConnectorException(ErrorCode.NO_SUCH_ATTRIBUTE,
                            "No such attribute: " + mod.attributeName());
                }
                if (mod.values().isEmpty()) {
                    current.clear();
                } else {
                    for (Object v : mod.values()) {
                        if (!current.remove(v)) {
                            throw new ConnectorException(ErrorCode.NO_SUCH_ATTRIBUTE,
                                    "No such value in " + mod.attributeName());
                        }
                    }
                }
            }
        }
        if (idx >= 0) {
            attributes.remove(idx);
        }
        if (!current.isEmpty()) {
            attributes.add(new Attribute(mod.attributeName(), current));
        }
    }

    private static int indexOf(List<Attribute> attributes, String name) {
        for (int i = 0; i < attributes.size(); i++) {
            if (attributes.get(i).hasName(name)) {
                return i;
            }
        }
        return -1;
    }

    private static ConnectorObject project(ConnectorObject object, SearchRequest request) {
        if (request.attributesToGet().isEmpty()) {
            return object;
        }
        return new ConnectorObject(object.objectType(), object.identifier(),
                object.attributes().stream().filter(a -> request.isRequested(a.name())).toList());
    }
}
