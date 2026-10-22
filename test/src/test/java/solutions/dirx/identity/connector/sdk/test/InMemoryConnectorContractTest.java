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
