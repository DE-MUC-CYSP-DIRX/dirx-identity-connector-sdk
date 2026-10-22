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
