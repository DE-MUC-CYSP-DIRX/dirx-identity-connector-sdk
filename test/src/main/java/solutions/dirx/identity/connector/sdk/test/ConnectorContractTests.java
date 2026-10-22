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

package solutions.dirx.identity.connector.sdk.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import solutions.dirx.identity.connector.sdk.api.Connector;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.CreateOp;
import solutions.dirx.identity.connector.sdk.api.CreateRequest;
import solutions.dirx.identity.connector.sdk.api.CreateResult;
import solutions.dirx.identity.connector.sdk.api.DeleteOp;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.SchemaOp;
import solutions.dirx.identity.connector.sdk.api.SearchOp;
import solutions.dirx.identity.connector.sdk.api.SearchRequest;
import solutions.dirx.identity.connector.sdk.api.TestOp;
import solutions.dirx.identity.connector.sdk.api.UpdateOp;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.ConnectorObject;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.api.model.SearchFilter;
import solutions.dirx.identity.connector.sdk.api.model.SecretValue;

/**
 * Contract tests every connector should pass. Extend this class in your test sources and
 * implement the abstract methods:
 *
 * <pre>{@code
 * class MyConnectorContractTest extends ConnectorContractTests {
 *     protected Connector newConnector() { return new MyConnector(); }
 *     protected ConnectorConfiguration configuration() { return MapConnectorConfiguration.of(Map.of(...)); }
 *     protected String objectType() { return "user"; }
 *     protected CreateRequest newObject(String suffix) {
 *         return new CreateRequest("user", Identifier.of("test-" + suffix),
 *                 List.of(Attribute.of("mail", suffix + "@example.com")));
 *     }
 * }
 * }</pre>
 *
 * <p>Tests for operations the connector does not implement are skipped. The tests create
 * objects with unique identifiers and delete them afterwards, so they can also run against
 * a real test system.
 */
public abstract class ConnectorContractTests {

    private ConnectorTestKit kit;
    private final List<Identifier> created = new ArrayList<>();

    /**
     * Creates a new, uninitialized connector instance.
     *
     * @return the connector
     */
    protected abstract Connector newConnector();

    /**
     * Returns the configuration for the connector under test.
     *
     * @return the configuration
     */
    protected abstract ConnectorConfiguration configuration();

    /**
     * Returns the object type used by the tests.
     *
     * @return the object type
     */
    protected abstract String objectType();

    /**
     * Returns a request that creates a new, valid object. Each suffix must lead to a distinct
     * object, for example by appending it to the identifier and unique attributes.
     *
     * @param suffix a unique suffix
     * @return the request
     */
    protected abstract CreateRequest newObject(String suffix);

    /**
     * Returns a modification applied by the update tests. Use a single-valued
     * {@link Modification.Operation#REPLACE REPLACE} of an attribute the target returns on read.
     *
     * @return the modification, or {@code null} to skip the update tests
     */
    protected Modification sampleModification() {
        return null;
    }

    /**
     * Returns a filter that matches the object created by {@link #newObject(String)}, used by
     * the filter test.
     *
     * @param request the create request
     * @return the filter, or {@code null} to skip the filter test
     */
    protected SearchFilter filterMatching(CreateRequest request) {
        return null;
    }

    /**
     * Returns the names of the attributes that the tests compare after create. By default,
     * these are all attributes of the create request that do not contain secrets.
     *
     * @param request the create request
     * @return the attribute names
     */
    protected List<String> verifiedAttributes(CreateRequest request) {
        return request.attributes().stream()
                .filter(a -> a.values().stream().noneMatch(SecretValue.class::isInstance))
                .map(Attribute::name)
                .toList();
    }

    /**
     * Returns the page size used by the paging test.
     *
     * @return the page size, at least 1
     */
    protected int pageSize() {
        return 2;
    }

    /**
     * Returns the kit driving the connector under test. Available in the test methods of
     * subclasses.
     *
     * @return the kit
     */
    protected final ConnectorTestKit kit() {
        return kit;
    }

    @BeforeEach
    void openConnector() throws ConnectorException {
        kit = ConnectorTestKit.open(newConnector(), configuration());
    }

    @AfterEach
    void cleanUpAndClose() {
        if (kit == null) {
            return;
        }
        try {
            if (kit.supports(DeleteOp.class)) {
                for (Identifier id : created) {
                    try {
                        kit.delete(objectType(), id);
                    } catch (ConnectorException e) {
                        // already deleted by the test
                    }
                }
            }
        } finally {
            created.clear();
            kit.close();
        }
    }

    @Test
    void closeCanBeCalledTwice() throws ConnectorException {
        Connector connector = newConnector();
        connector.init(new TestConnectorContext("close-test", configuration()));
        connector.close();
        connector.close();
    }

    @Test
    void testOperationSucceeds() throws ConnectorException {
        assumeTrue(kit.supports(TestOp.class), "TestOp not implemented");
        kit.test();
    }

    @Test
    void schemaIsAvailable() throws ConnectorException {
        assumeTrue(kit.supports(SchemaOp.class), "SchemaOp not implemented");
        assertNotNull(kit.schema(), "schema()");
    }

    @Test
    void createdObjectCanBeRead() throws ConnectorException {
        assumeCapabilities(CreateOp.class, SearchOp.class);
        CreateRequest request = newObject(suffix());
        Identifier id = create(request);
        ConnectorObject object = kit.read(objectType(), id)
                .orElseGet(() -> fail("Created object " + id + " not found"));
        for (String name : verifiedAttributes(request)) {
            List<String> expected = request.attribute(name).map(Attribute::strings).orElse(List.of());
            List<String> actual = object.attribute(name).map(Attribute::strings).orElse(List.of());
            assertEquals(lower(expected), lower(actual), "values of " + name);
        }
    }

    @Test
    void createOfExistingObjectFailsWithAlreadyExists() throws ConnectorException {
        assumeCapabilities(CreateOp.class);
        CreateRequest request = newObject(suffix());
        assumeTrue(request.identifier() != null, "target assigns identifiers");
        create(request);
        assertErrorCode(ErrorCode.ALREADY_EXISTS, () -> kit.create(request));
    }

    @Test
    void updatedValueCanBeRead() throws ConnectorException {
        assumeCapabilities(CreateOp.class, UpdateOp.class, SearchOp.class);
        Modification mod = sampleModification();
        assumeTrue(mod != null, "no sample modification");
        Identifier id = create(newObject(suffix()));
        Identifier current = Optional.ofNullable(kit.update(objectType(), id, mod).identifier()).orElse(id);
        replaceCreated(id, current);
        ConnectorObject object = kit.read(objectType(), current)
                .orElseGet(() -> fail("Updated object " + current + " not found"));
        if (mod.operation() == Modification.Operation.REPLACE) {
            List<String> expected = mod.asAttribute().strings();
            List<String> actual = object.attribute(mod.attributeName()).map(Attribute::strings).orElse(List.of());
            assertEquals(lower(expected), lower(actual), "values of " + mod.attributeName());
        }
    }

    @Test
    void updateOfMissingObjectFailsWithNotFound() {
        assumeCapabilities(UpdateOp.class);
        Modification mod = sampleModification();
        assumeTrue(mod != null, "no sample modification");
        assertErrorCode(ErrorCode.NOT_FOUND, () -> kit.update(objectType(), missingIdentifier(), mod));
    }

    @Test
    void deletedObjectIsGone() throws ConnectorException {
        assumeCapabilities(CreateOp.class, DeleteOp.class, SearchOp.class);
        Identifier id = create(newObject(suffix()));
        kit.delete(objectType(), id);
        created.remove(id);
        assertTrue(kit.read(objectType(), id).isEmpty(), "object still exists after delete");
    }

    @Test
    void deleteOfMissingObjectFailsWithNotFound() {
        assumeCapabilities(DeleteOp.class);
        assertErrorCode(ErrorCode.NOT_FOUND, () -> kit.delete(objectType(), missingIdentifier()));
    }

    @Test
    void readOfMissingObjectIsEmpty() throws ConnectorException {
        assumeCapabilities(SearchOp.class);
        assertTrue(kit.read(objectType(), missingIdentifier()).isEmpty());
    }

    @Test
    void pagedSearchReturnsAllObjects() throws ConnectorException {
        assumeCapabilities(CreateOp.class, SearchOp.class);
        int count = pageSize() * 2 + 1;
        List<Identifier> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(create(newObject(suffix())));
        }
        SearchRequest request = SearchRequest.of(objectType(), null).withPage(pageSize(), null);
        List<String> found = kit.searchAll(request).stream()
                .map(o -> o.identifier().value().toLowerCase(Locale.ROOT))
                .toList();
        for (Identifier id : ids) {
            assertTrue(found.contains(id.value().toLowerCase(Locale.ROOT)), "paged search misses " + id);
        }
        assertEquals(found.size(), found.stream().distinct().count(), "paged search returned duplicates");
    }

    @Test
    void searchHandlerCanStopEarly() throws ConnectorException {
        assumeCapabilities(CreateOp.class, SearchOp.class);
        create(newObject(suffix()));
        create(newObject(suffix()));
        int[] calls = {0};
        ((SearchOp) kit.connector()).search(SearchRequest.of(objectType(), null), o -> {
            calls[0]++;
            return false;
        });
        assertEquals(1, calls[0], "handler called after it returned false");
    }

    @Test
    void filterFindsCreatedObject() throws ConnectorException {
        assumeCapabilities(CreateOp.class, SearchOp.class);
        CreateRequest request = newObject(suffix());
        SearchFilter filter = filterMatching(request);
        assumeTrue(filter != null, "no filter for the filter test");
        Identifier id = create(request);
        List<ConnectorObject> hits = kit.search(objectType(), filter);
        assertTrue(hits.stream().anyMatch(o -> o.identifier().value().equalsIgnoreCase(id.value())),
                "filter " + filter.toLdapString() + " does not find " + id);
    }

    /**
     * Returns an identifier of an object that does not exist.
     *
     * @return the identifier
     */
    protected Identifier missingIdentifier() {
        CreateRequest template = newObject("missing-" + suffix());
        return template.identifier() != null ? template.identifier() : Identifier.of("missing-" + suffix());
    }

    /**
     * Creates an object and registers it for deletion after the test.
     *
     * @param request the request
     * @return the identifier of the new object
     * @throws ConnectorException on failure
     */
    protected final Identifier create(CreateRequest request) throws ConnectorException {
        CreateResult result = kit.create(request);
        Identifier id = result.identifier() != null ? result.identifier() : request.identifier();
        assertNotNull(id, "create returned no identifier and the request had none");
        created.add(id);
        return id;
    }

    private void replaceCreated(Identifier oldId, Identifier newId) {
        if (!oldId.equals(newId)) {
            created.remove(oldId);
            created.add(newId);
        }
    }

    private void assumeCapabilities(Class<?>... operations) {
        for (Class<?> op : operations) {
            assumeTrue(kit.supports(op), op.getSimpleName() + " not implemented");
        }
    }

    private static void assertErrorCode(ErrorCode expected, ThrowingCall call) {
        ConnectorException e = assertThrows(ConnectorException.class, call::run);
        assertEquals(expected, e.getErrorCode(), () -> "error code of " + e);
    }

    private static List<String> lower(List<String> values) {
        return values.stream().map(v -> v.toLowerCase(Locale.ROOT)).sorted().toList();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws ConnectorException;
    }
}
