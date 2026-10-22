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

import java.util.List;
import java.util.Map;

import solutions.dirx.identity.connector.sdk.api.Connector;
import solutions.dirx.identity.connector.sdk.api.CreateRequest;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.config.MapConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.Filters;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.api.model.SearchFilter;
import solutions.dirx.identity.connector.sdk.api.model.SecretValue;

class InMemoryConnectorContractTest extends ConnectorContractTests {

    @Override
    protected Connector newConnector() {
        return new InMemoryConnector();
    }

    @Override
    protected ConnectorConfiguration configuration() {
        return MapConnectorConfiguration.of(Map.of());
    }

    @Override
    protected String objectType() {
        return "user";
    }

    @Override
    protected CreateRequest newObject(String suffix) {
        return new CreateRequest("user", Identifier.of("user-" + suffix), List.of(
                Attribute.of("mail", suffix + "@example.com"),
                Attribute.of("memberOf", "a", "b"),
                Attribute.of("password", SecretValue.of("secret"))));
    }

    @Override
    protected Modification sampleModification() {
        return Modification.replace("mail", "changed@example.com");
    }

    @Override
    protected SearchFilter filterMatching(CreateRequest request) {
        return Filters.eq("mail", request.attribute("mail").orElseThrow().firstString().orElseThrow());
    }
}
