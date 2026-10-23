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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

import solutions.dirx.identity.connector.sdk.api.Connector;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.CreateOp;
import solutions.dirx.identity.connector.sdk.api.CreateRequest;
import solutions.dirx.identity.connector.sdk.api.CreateResult;
import solutions.dirx.identity.connector.sdk.api.DeleteOp;
import solutions.dirx.identity.connector.sdk.api.DeleteRequest;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.SchemaOp;
import solutions.dirx.identity.connector.sdk.api.SearchOp;
import solutions.dirx.identity.connector.sdk.api.SearchRequest;
import solutions.dirx.identity.connector.sdk.api.SearchResult;
import solutions.dirx.identity.connector.sdk.api.TestOp;
import solutions.dirx.identity.connector.sdk.api.UpdateOp;
import solutions.dirx.identity.connector.sdk.api.UpdateRequest;
import solutions.dirx.identity.connector.sdk.api.UpdateResult;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.ConnectorObject;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.api.model.Schema;
import solutions.dirx.identity.connector.sdk.api.model.SearchFilter;

/**
 * Drives a connector the way a host does, with convenience methods for tests and
 * experiments.
 *
 * <pre>{@code
 * try (ConnectorTestKit kit = ConnectorTestKit.open(new MyConnector(), config)) {
 *     kit.create("user", Identifier.of("jdoe"), Attribute.of("mail", "jdoe@example.com"));
 *     Optional<ConnectorObject> user = kit.read("user", Identifier.of("jdoe"));
 * }
 * }</pre>
 *
 * <p>Calling an operation the connector does not implement throws a
 * {@link ConnectorException} with {@link ErrorCode#UNSUPPORTED_OPERATION}.
 */
public final class ConnectorTestKit implements AutoCloseable {

    private final Connector connector;
    private final TestConnectorContext context;

    private ConnectorTestKit(Connector connector, TestConnectorContext context) {
        this.connector = connector;
        this.context = context;
    }

    /**
     * Initializes a connector with a recording test context named {@code "test"}.
     *
     * @param connector     the connector, not yet initialized
     * @param configuration the configuration
     * @return the kit; close it to close the connector
     * @throws ConnectorException if initialization fails
     */
    public static ConnectorTestKit open(Connector connector, ConnectorConfiguration configuration)
            throws ConnectorException {
        return open(connector, new TestConnectorContext("test", configuration));
    }

    /**
     * Initializes a connector with the given context.
     *
     * @param connector the connector, not yet initialized
     * @param context   the context
     * @return the kit; close it to close the connector
     * @throws ConnectorException if initialization fails
     */
    public static ConnectorTestKit open(Connector connector, TestConnectorContext context)
            throws ConnectorException {
        Objects.requireNonNull(connector, "connector");
        Objects.requireNonNull(context, "context");
        connector.init(context);
        return new ConnectorTestKit(connector, context);
    }

    /**
     * Returns the connector.
     *
     * @return the connector
     */
    public Connector connector() {
        return connector;
    }

    /**
     * Returns the context, including the recorded log.
     *
     * @return the context
     */
    public TestConnectorContext context() {
        return context;
    }

    /**
     * Tests whether the connector implements an operation interface.
     *
     * @param operation the operation interface, for example {@code CreateOp.class}
     * @return {@code true} if supported
     */
    public boolean supports(Class<?> operation) {
        return operation.isInstance(connector);
    }

    /**
     * Calls {@link CreateOp#create}.
     *
     * @param request the request
     * @return the result
     * @throws ConnectorException on failure
     */
    public CreateResult create(CreateRequest request) throws ConnectorException {
        return op(CreateOp.class).create(request);
    }

    /**
     * Creates an object from attributes.
     *
     * @param objectType the object type
     * @param identifier the requested identifier
     * @param attributes the attributes
     * @return the result
     * @throws ConnectorException on failure
     */
    public CreateResult create(String objectType, Identifier identifier,
                               Attribute... attributes)
            throws ConnectorException {
        return create(new CreateRequest(objectType, identifier, List.of(attributes)));
    }

    /**
     * Calls {@link UpdateOp#update}.
     *
     * @param request the request
     * @return the result
     * @throws ConnectorException on failure
     */
    public UpdateResult update(UpdateRequest request) throws ConnectorException {
        return op(UpdateOp.class).update(request);
    }

    /**
     * Updates an object.
     *
     * @param objectType    the object type
     * @param identifier    the identifier
     * @param modifications the modifications
     * @return the result
     * @throws ConnectorException on failure
     */
    public UpdateResult update(String objectType, Identifier identifier, Modification... modifications)
            throws ConnectorException {
        return update(new UpdateRequest(objectType, identifier, List.of(modifications)));
    }

    /**
     * Deletes an object.
     *
     * @param objectType the object type
     * @param identifier the identifier
     * @throws ConnectorException on failure
     */
    public void delete(String objectType, Identifier identifier) throws ConnectorException {
        op(DeleteOp.class).delete(new DeleteRequest(objectType, identifier));
    }

    /**
     * Reads one object. A {@link ErrorCode#NOT_FOUND} error is mapped to an empty result.
     *
     * @param objectType the object type
     * @param identifier the identifier
     * @return the object, if it exists
     * @throws ConnectorException on failure
     */
    public Optional<ConnectorObject> read(String objectType, Identifier identifier) throws ConnectorException {
        try {
            List<ConnectorObject> hits = searchAll(SearchRequest.read(objectType, identifier));
            if (hits.size() > 1) {
                throw new ConnectorException(ErrorCode.OTHER,
                        "Read of " + identifier + " returned " + hits.size() + " objects");
            }
            return hits.stream().findFirst();
        } catch (ConnectorException e) {
            if (e.getErrorCode() == ErrorCode.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * Searches all objects of a type matching a filter, following all pages.
     *
     * @param objectType the object type
     * @param filter     the filter, or {@code null} for all objects
     * @return all hits
     * @throws ConnectorException on failure
     */
    public List<ConnectorObject> search(String objectType, @Nullable SearchFilter filter) throws ConnectorException {
        return searchAll(SearchRequest.of(objectType, filter));
    }

    /**
     * Executes a search and follows all pages. If the request has a page size, each further
     * page is requested with the cookie returned by the previous one.
     *
     * @param request the request
     * @return all hits
     * @throws ConnectorException on failure, or if the connector returns the same cookie twice
     */
    public List<ConnectorObject> searchAll(SearchRequest request) throws ConnectorException {
        SearchOp op = op(SearchOp.class);
        List<ConnectorObject> hits = new ArrayList<>();
        SearchRequest current = request;
        @Nullable String previousCookie = null;
        while (true) {
            SearchResult result = op.search(current, o -> {
                hits.add(o);
                return true;
            });
            if (result == null || !result.hasMorePages()) {
                return hits;
            }
            if (result.nextPageCookie().equals(previousCookie)) {
                throw new ConnectorException(ErrorCode.OTHER,
                        "Connector returned the same page cookie twice: " + previousCookie);
            }
            previousCookie = result.nextPageCookie();
            current = current.withPage(request.pageSize(), previousCookie);
        }
    }

    /**
     * Calls {@link SchemaOp#schema}.
     *
     * @return the schema
     * @throws ConnectorException on failure
     */
    public Schema schema() throws ConnectorException {
        return op(SchemaOp.class).schema();
    }

    /**
     * Calls {@link TestOp#test}.
     *
     * @throws ConnectorException on failure
     */
    public void test() throws ConnectorException {
        op(TestOp.class).test();
    }

    /** Closes the connector. */
    @Override
    public void close() {
        connector.close();
    }

    private <T> T op(Class<T> type) throws ConnectorException {
        if (!type.isInstance(connector)) {
            throw new ConnectorException(ErrorCode.UNSUPPORTED_OPERATION,
                    connector.getClass().getName() + " does not implement " + type.getSimpleName());
        }
        return type.cast(connector);
    }
}
