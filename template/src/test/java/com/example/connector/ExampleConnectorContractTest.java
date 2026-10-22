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

package com.example.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ServiceLoader;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import solutions.dirx.identity.connector.sdk.api.Connector;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.CreateRequest;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.config.ConnectionSettings;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorContext;
import solutions.dirx.identity.connector.sdk.api.config.MapConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.Filters;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.api.model.SearchFilter;
import solutions.dirx.identity.connector.sdk.test.ConnectorContractTests;

/**
 * Runs the SDK contract tests against the connector.
 *
 * <p>TODO: Point {@link #configuration()} to a test instance of your target system,
 * and adapt the sample objects to its schema.
 */
class ExampleConnectorContractTest extends ConnectorContractTests {

    private final String url = "memory://" + UUID.randomUUID();

    @Override
    protected Connector newConnector() {
        return new ExampleConnector();
    }

    @Override
    protected ConnectorConfiguration configuration() {
        return MapConnectorConfiguration.builder()
                .connection(ConnectionSettings.builder().url(url).build())
                .build();
    }

    @Override
    protected String objectType() {
        return ExampleConnector.ACCOUNT;
    }

    @Override
    protected CreateRequest newObject(String suffix) {
        return new CreateRequest(ExampleConnector.ACCOUNT, Identifier.of("user-" + suffix), List.of(
                Attribute.of("displayName", "User " + suffix),
                Attribute.of("mail", suffix + "@example.com")));
    }

    @Override
    protected Modification sampleModification() {
        return Modification.replace("mail", "changed@example.com");
    }

    @Override
    protected SearchFilter filterMatching(CreateRequest request) {
        return Filters.eq("mail", request.attribute("mail").orElseThrow().firstString().orElseThrow());
    }

    @Test
    void isRegisteredAsService() {
        List<Class<?>> types = ServiceLoader.load(Connector.class).stream()
                .<Class<?>>map(ServiceLoader.Provider::type).toList();
        assertTrue(types.contains(ExampleConnector.class), "service registration missing: " + types);
    }

    @Test
    void missingUrlIsAConfigurationError() {
        ConnectorException e = assertThrows(ConnectorException.class, () -> new ExampleConnector()
                .init(ConnectorContext.of("test", MapConnectorConfiguration.builder().build())));
        assertEquals(ErrorCode.CONFIGURATION, e.getErrorCode());
    }
}
