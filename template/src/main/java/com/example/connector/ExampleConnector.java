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

package com.example.connector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import solutions.dirx.identity.connector.sdk.api.Connector;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.CreateOp;
import solutions.dirx.identity.connector.sdk.api.CreateRequest;
import solutions.dirx.identity.connector.sdk.api.CreateResult;
import solutions.dirx.identity.connector.sdk.api.DeleteOp;
import solutions.dirx.identity.connector.sdk.api.DeleteRequest;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.ResultHandler;
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
import solutions.dirx.identity.connector.sdk.api.model.SearchScope;
import solutions.dirx.identity.connector.sdk.api.spi.ConnectorInfo;

/**
 * Starting point for a new connector.
 *
 * <p>Steps to adapt it:
 * <ol>
 *   <li>Rename the package and class, and update
 *       {@code META-INF/services/solutions.dirx.identity.connector.sdk.api.Connector}.</li>
 *   <li>Replace {@link ExampleClient} with the client of your target system.</li>
 *   <li>Map target system errors to {@link ErrorCode}s; DirX Identity workflows rely on them.</li>
 *   <li>Remove operation interfaces your target system does not support.</li>
 *   <li>Adapt {@code ExampleConnectorContractTest}, ideally against a test instance of the target.</li>
 * </ol>
 */
@ConnectorInfo(name = "example", version = "1.0.0", displayName = "Example connector",
        description = "Template connector. Replace with a description of your connector.",
        objectTypes = ExampleConnector.ACCOUNT)
public class ExampleConnector implements Connector, CreateOp, UpdateOp, DeleteOp, SearchOp, TestOp {

    /** The supported object type. */
    public static final String ACCOUNT = "Account";

    /** Attribute holding the user identifier in the target system. */
    static final String ID = "id";

    private static final int DEFAULT_PAGE_SIZE = 100;

    private ExampleClient client;
    private System.Logger log;

    /** Creates an uninitialized connector. DirX Identity calls {@link #init} afterwards. */
    public ExampleConnector() {
    }

    @Override
    public void init(ConnectorContext context) throws ConnectorException {
        log = context.logger(ExampleConnector.class);
        String url = context.configuration().connection().urlValue()
                .orElseThrow(() -> new ConnectorException(ErrorCode.CONFIGURATION, "The connection URL is not configured"));
        // Read further settings with context.configuration().get(...), getInt(...), getSecret(...).
        client = new ExampleClient(url);
        log.log(System.Logger.Level.DEBUG, "Connected {0} to {1}", context.instanceName(), url);
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    @Override
    public void test() throws ConnectorException {
        client().ping();
    }

    @Override
    public CreateResult create(CreateRequest request) throws ConnectorException {
        checkType(request.objectType());
        if (request.identifier() == null) {
            throw new ConnectorException(ErrorCode.INVALID_REQUEST, "An identifier is required");
        }
        Map<String, List<Object>> attributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        request.attributes().forEach(a -> attributes.put(a.name(), a.values()));
        try {
            client().createUser(request.identifier().value(), attributes);
        } catch (ExampleClient.UserExistsException e) {
            throw new ConnectorException(ErrorCode.ALREADY_EXISTS, e.getMessage(), e);
        }
        return CreateResult.of(request.identifier());
    }

    @Override
    public UpdateResult update(UpdateRequest request) throws ConnectorException {
        checkType(request.objectType());
        String id = request.identifier().value();
        try {
            Map<String, List<Object>> user = client().getUser(id);
            for (Modification mod : request.modifications()) {
                apply(user, mod);
            }
            client().putUser(id, user);
        } catch (ExampleClient.NoSuchUserException e) {
            throw new ConnectorException(ErrorCode.NOT_FOUND, e.getMessage(), e);
        }
        return UpdateResult.unchanged();
    }

    @Override
    public void delete(DeleteRequest request) throws ConnectorException {
        checkType(request.objectType());
        try {
            client().deleteUser(request.identifier().value());
        } catch (ExampleClient.NoSuchUserException e) {
            throw new ConnectorException(ErrorCode.NOT_FOUND, e.getMessage(), e);
        }
    }

    @Override
    public SearchResult search(SearchRequest request, ResultHandler handler) throws ConnectorException {
        checkType(request.objectType());
        if (request.scope() == SearchScope.OBJECT && request.base() != null) {
            try {
                ConnectorObject object = toObject(client().getUser(request.base().value()), request);
                if (FilterEvaluator.matches(request.filter(), object)) {
                    handler.handle(object);
                }
            } catch (ExampleClient.NoSuchUserException e) {
                // A missing object yields an empty result, not an error.
            }
            return SearchResult.done();
        }
        // Push the filter down to the target if it supports filtering; this template
        // evaluates it locally. The page cookie is the offset of the next page.
        int pageSize = request.pageSize() > 0 ? request.pageSize() : DEFAULT_PAGE_SIZE;
        int offset = offset(request.pageCookie());
        List<String> ids = client().listUsers(offset, pageSize);
        for (String id : ids) {
            ConnectorObject object;
            try {
                object = toObject(client().getUser(id), request);
            } catch (ExampleClient.NoSuchUserException e) {
                continue; // deleted concurrently
            }
            if (FilterEvaluator.matches(request.filter(), object) && !handler.handle(object)) {
                return SearchResult.done();
            }
        }
        return ids.size() < pageSize ? SearchResult.done()
                : SearchResult.nextPage(Integer.toString(offset + ids.size()));
    }

    private ExampleClient client() throws ConnectorException {
        ExampleClient c = client;
        if (c == null) {
            throw new ConnectorException(ErrorCode.CONFIGURATION, "Connector is not initialized");
        }
        return c;
    }

    private static void checkType(String objectType) throws ConnectorException {
        if (!ACCOUNT.equalsIgnoreCase(objectType)) {
            throw new ConnectorException(ErrorCode.UNSUPPORTED_OPERATION, "Unsupported object type: " + objectType);
        }
    }

    private static int offset(String cookie) throws ConnectorException {
        try {
            return cookie == null || cookie.isEmpty() ? 0 : Integer.parseInt(cookie);
        } catch (NumberFormatException e) {
            throw new ConnectorException(ErrorCode.INVALID_REQUEST, "Invalid page cookie: " + cookie, e);
        }
    }

    private static ConnectorObject toObject(Map<String, List<Object>> user, SearchRequest request) {
        String id = (String) user.get(ID).getFirst();
        ConnectorObject.Builder builder = ConnectorObject.builder(ACCOUNT, Identifier.of(id));
        user.forEach((name, values) -> {
            if (!ID.equalsIgnoreCase(name) && (request.attributesToGet().isEmpty() || request.isRequested(name))) {
                builder.attribute(new Attribute(name, values));
            }
        });
        return builder.build();
    }

    private static void apply(Map<String, List<Object>> user, Modification mod) throws ConnectorException {
        String name = mod.attributeName();
        List<Object> values = new ArrayList<>(user.getOrDefault(name, List.of()));
        switch (mod.operation()) {
            case REPLACE -> values = new ArrayList<>(mod.values());
            case ADD -> {
                for (Object v : mod.values()) {
                    if (values.contains(v)) {
                        throw new ConnectorException(ErrorCode.ATTRIBUTE_OR_VALUE_EXISTS, "Value exists in " + name);
                    }
                    values.add(v);
                }
            }
            case DELETE -> {
                if (!user.containsKey(name)) {
                    throw new ConnectorException(ErrorCode.NO_SUCH_ATTRIBUTE, "No such attribute: " + name);
                }
                if (mod.values().isEmpty()) {
                    values.clear();
                } else if (!values.removeAll(mod.values())) {
                    throw new ConnectorException(ErrorCode.NO_SUCH_ATTRIBUTE, "No such value in " + name);
                }
            }
        }
        if (values.isEmpty()) {
            user.remove(name);
        } else {
            user.put(name, values);
        }
    }
}
