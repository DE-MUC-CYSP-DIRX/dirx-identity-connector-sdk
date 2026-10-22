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

package solutions.dirx.identity.connector.sdk.jdbc;

import java.util.List;
import solutions.dirx.identity.connector.sdk.api.Connector;
import solutions.dirx.identity.connector.sdk.api.CreateRequest;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.Filters;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.api.model.SearchFilter;
import solutions.dirx.identity.connector.sdk.test.ConnectorContractTests;

class JdbcConnectorContractTest extends ConnectorContractTests {

    private static final String URL = TestDatabase.create();

    @Override
    protected Connector newConnector() {
        return new JdbcConnector();
    }

    @Override
    protected ConnectorConfiguration configuration() {
        return TestDatabase.configuration(URL).build();
    }

    @Override
    protected String objectType() {
        return "Account";
    }

    @Override
    protected CreateRequest newObject(String suffix) {
        return new CreateRequest("Account", Identifier.of("acc-" + suffix), List.of(
                Attribute.of("displayName", "Account " + suffix),
                Attribute.of("givenName", "Given " + suffix),
                Attribute.of("age", 42)));
    }

    @Override
    protected List<String> verifiedAttributes(CreateRequest request) {
        return List.of("DISPLAYNAME", "GIVENNAME", "AGE");
    }

    @Override
    protected Modification sampleModification() {
        return Modification.replace("GIVENNAME", "changed");
    }

    @Override
    protected SearchFilter filterMatching(CreateRequest request) {
        return Filters.eq("DISPLAYNAME", request.attribute("displayName").flatMap(Attribute::firstString).orElseThrow());
    }
}
