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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import solutions.dirx.identity.connector.sdk.api.Connector;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
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
import solutions.dirx.identity.connector.sdk.test.ConnectorContractTests;

class UserApiConnectorContractTest extends ConnectorContractTests {

    private static final FakeUserApi SERVER;

    static {
        try {
            SERVER = new FakeUserApi();
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
        return new UserApiConnector();
    }

    @Override
    protected ConnectorConfiguration configuration() {
        return MapConnectorConfiguration.builder()
                .connection(ConnectionSettings.builder().url(SERVER.baseUri().toString()).build())
                .build();
    }

    @Override
    protected String objectType() {
        return UserApiConnector.USER;
    }

    @Override
    protected CreateRequest newObject(String suffix) {
        return new CreateRequest(UserApiConnector.USER, Identifier.of("user-" + suffix), List.of(
                Attribute.of("email", suffix + "@example.com"),
                Attribute.of("name.given", "Given " + suffix),
                Attribute.of("roles", "reader")));
    }

    @Override
    protected Modification sampleModification() {
        return Modification.replace("name.family", "Changed");
    }

    @Override
    protected SearchFilter filterMatching(CreateRequest request) {
        return Filters.and(
                Filters.eq("email", request.attribute("email").orElseThrow().firstString().orElseThrow()),
                Filters.startsWith("name.given", "Given"));
    }

    @Test
    void equalityFilterIsSentToTheServer() throws ConnectorException {
        create(newObject("filter"));
        SERVER.requests.clear();
        List<ConnectorObject> hits = kit().search(UserApiConnector.USER, Filters.eq("email", "filter@example.com"));
        assertEquals(1, hits.size());
        assertTrue(SERVER.requests.stream().anyMatch(r -> r.contains("email=filter%40example.com")),
                () -> "requests: " + SERVER.requests);
    }

    @Test
    void multiValuedAttributeStaysAnArray() throws ConnectorException {
        Identifier id = create(newObject("multi"));
        kit().update(UserApiConnector.USER, id, Modification.add("roles", "writer"));
        assertEquals(List.of("reader", "writer"),
                kit().read(UserApiConnector.USER, id).orElseThrow().attribute("roles").orElseThrow().strings());
        kit().update(UserApiConnector.USER, id, Modification.delete("roles", "reader", "writer"));
        assertTrue(kit().read(UserApiConnector.USER, id).orElseThrow().attribute("roles").isEmpty());
    }
}
