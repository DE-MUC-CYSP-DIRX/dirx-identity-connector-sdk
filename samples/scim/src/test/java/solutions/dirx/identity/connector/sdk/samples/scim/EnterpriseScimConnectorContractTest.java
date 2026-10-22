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

package solutions.dirx.identity.connector.sdk.samples.scim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import solutions.dirx.identity.connector.sdk.api.Connector;
import solutions.dirx.identity.connector.sdk.api.CreateRequest;
import solutions.dirx.identity.connector.sdk.api.config.ConnectionSettings;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.config.MapConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.ConnectorObject;
import solutions.dirx.identity.connector.sdk.api.model.Filters;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.api.model.SearchFilter;
import solutions.dirx.identity.connector.sdk.scim.FakeScimServer;
import solutions.dirx.identity.connector.sdk.test.ConnectorContractTests;

class EnterpriseScimConnectorContractTest extends ConnectorContractTests {

    private static final FakeScimServer SERVER;

    static {
        try {
            SERVER = new FakeScimServer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterAll
    static void stopServer() {
        SERVER.close();
    }

    @Override
    protected Connector newConnector() {
        return new EnterpriseScimConnector();
    }

    @Override
    protected ConnectorConfiguration configuration() {
        return MapConnectorConfiguration.builder()
                .connection(ConnectionSettings.builder().url(SERVER.baseUri().toString()).build())
                .build();
    }

    @Override
    protected String objectType() {
        return EnterpriseScimConnector.ACCOUNT;
    }

    @Override
    protected CreateRequest newObject(String suffix) {
        return new CreateRequest(EnterpriseScimConnector.ACCOUNT, Identifier.of("account-" + suffix), List.of(
                Attribute.of("name.givenName", "Given" + suffix),
                Attribute.of("name.familyName", "Family"),
                Attribute.of("employeeNumber", "E-" + suffix),
                Attribute.of("department", "Sales")));
    }

    @Override
    protected List<String> verifiedAttributes(CreateRequest request) {
        return List.of("name.givenName", "employeeNumber", "department");
    }

    @Override
    protected Modification sampleModification() {
        return Modification.replace("department", "Marketing");
    }

    @Override
    protected SearchFilter filterMatching(CreateRequest request) {
        return Filters.eq("userName", request.identifier().value());
    }

    @Test
    void storesExtensionAttributesInEnterpriseSchemaAndDerivesDisplayName() throws Exception {
        CreateRequest request = newObject("ext");
        create(request);
        ObjectNode stored = SERVER.users().values().stream()
                .filter(u -> "account-ext".equals(u.path("userName").asString("")))
                .findFirst().orElseThrow();
        assertEquals("Givenext Family", stored.path("displayName").asString(""));
        assertEquals("E-ext", stored.path(EnterpriseScimConnector.ENTERPRISE_SCHEMA)
                .path("employeeNumber").asString(""));
        assertTrue(stored.path("schemas").toString().contains(EnterpriseScimConnector.ENTERPRISE_SCHEMA));

        ConnectorObject read = kit().read(EnterpriseScimConnector.ACCOUNT, idOf(stored)).orElseThrow();
        assertEquals("E-ext", read.firstString("employeeNumber").orElseThrow());
        assertTrue(read.attribute(EnterpriseScimConnector.ENTERPRISE_SCHEMA + ".employeeNumber").isEmpty());
    }

    @Test
    void patchesExtensionAttributesWithSchemaQualifiedPath() throws Exception {
        create(newObject("patch"));
        ObjectNode stored = SERVER.users().values().stream()
                .filter(u -> "account-patch".equals(u.path("userName").asString("")))
                .findFirst().orElseThrow();
        SERVER.requests.clear();
        kit().update(EnterpriseScimConnector.ACCOUNT, idOf(stored), Modification.replace("employeeNumber", "E-9"));
        assertTrue(SERVER.requests.stream().anyMatch(r -> "PATCH".equals(r.method())
                && r.body().contains(EnterpriseScimConnector.ENTERPRISE_SCHEMA + ":employeeNumber")));
        assertEquals("E-9", SERVER.users().get(stored.path("id").asString()).path(
                EnterpriseScimConnector.ENTERPRISE_SCHEMA).path("employeeNumber").asString(""));
    }

    private static Identifier idOf(ObjectNode stored) {
        return Identifier.of(stored.path("id").asString());
    }
}
