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

package solutions.dirx.identity.connector.sdk.scim;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import solutions.dirx.identity.connector.sdk.api.Connector;
import solutions.dirx.identity.connector.sdk.api.CreateRequest;
import solutions.dirx.identity.connector.sdk.api.config.ConnectionSettings;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.config.MapConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.Filters;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.api.model.SearchFilter;
import solutions.dirx.identity.connector.sdk.test.ConnectorContractTests;

class ScimConnectorContractTest extends ConnectorContractTests {

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
        return new ScimConnector();
    }

    @Override
    protected ConnectorConfiguration configuration() {
        return MapConnectorConfiguration.builder()
                .connection(ConnectionSettings.builder().url(SERVER.baseUri().toString()).build())
                .property(ScimConnector.PROP_CREATE_IDENTIFIER_ATTRIBUTE, "userName")
                .build();
    }

    @Override
    protected String objectType() {
        return "User";
    }

    @Override
    protected CreateRequest newObject(String suffix) {
        return new CreateRequest("User", Identifier.of("user-" + suffix), List.of(
                Attribute.of("name.givenName", "Given " + suffix),
                Attribute.of("displayName", "User " + suffix)));
    }

    @Override
    protected List<String> verifiedAttributes(CreateRequest request) {
        return List.of("name.givenName", "displayName");
    }

    @Override
    protected Modification sampleModification() {
        return Modification.replace("displayName", "changed");
    }

    @Override
    protected SearchFilter filterMatching(CreateRequest request) {
        return Filters.eq("userName", request.identifier().value());
    }
}
