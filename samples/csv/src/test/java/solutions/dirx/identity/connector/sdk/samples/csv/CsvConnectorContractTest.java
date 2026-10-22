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

package solutions.dirx.identity.connector.sdk.samples.csv;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.io.TempDir;

import solutions.dirx.identity.connector.sdk.api.Connector;
import solutions.dirx.identity.connector.sdk.api.CreateRequest;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.config.MapConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.Filters;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.api.model.SearchFilter;
import solutions.dirx.identity.connector.sdk.test.ConnectorContractTests;

class CsvConnectorContractTest extends ConnectorContractTests {

    @TempDir
    Path dir;

    @Override
    protected Connector newConnector() {
        return new CsvConnector();
    }

    @Override
    protected ConnectorConfiguration configuration() {
        return MapConnectorConfiguration.builder()
                .property(CsvConnector.PROP_FILE, dir.resolve("accounts.csv").toString())
                .property(CsvConnector.PROP_COLUMNS, "id", "displayName", "mail", "groups")
                .build();
    }

    @Override
    protected String objectType() {
        return "Account";
    }

    @Override
    protected CreateRequest newObject(String suffix) {
        return new CreateRequest("Account", Identifier.of("user-" + suffix), List.of(
                Attribute.of("displayName", "User, \"" + suffix + "\""),
                Attribute.of("mail", suffix + "@example.com"),
                Attribute.of("groups", "a", "b")));
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
