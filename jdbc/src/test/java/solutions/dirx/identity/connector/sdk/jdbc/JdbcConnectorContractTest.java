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
