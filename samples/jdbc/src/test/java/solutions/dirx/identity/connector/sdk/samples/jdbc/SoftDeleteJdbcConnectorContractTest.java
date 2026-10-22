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

package solutions.dirx.identity.connector.sdk.samples.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import solutions.dirx.identity.connector.sdk.api.Connector;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.CreateRequest;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.SearchRequest;
import solutions.dirx.identity.connector.sdk.api.config.ConnectionSettings;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.config.MapConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.Filters;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.api.model.SearchFilter;
import solutions.dirx.identity.connector.sdk.api.model.SecretValue;
import solutions.dirx.identity.connector.sdk.jdbc.JdbcConnector;
import solutions.dirx.identity.connector.sdk.test.ConnectorContractTests;

class SoftDeleteJdbcConnectorContractTest extends ConnectorContractTests {

    private static final String URL = createDatabase();

    private static String createDatabase() {
        String url = "jdbc:hsqldb:mem:" + UUID.randomUUID() + ";shutdown=false";
        try (Connection c = DriverManager.getConnection(url, "SA", ""); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE EMPLOYEES (ID VARCHAR(64) PRIMARY KEY, DISPLAYNAME VARCHAR(100), "
                    + "DEPARTMENT VARCHAR(100), ACTIVE BOOLEAN DEFAULT FALSE NOT NULL)");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return url;
    }

    @Override
    protected Connector newConnector() {
        return new SoftDeleteJdbcConnector();
    }

    @Override
    protected ConnectorConfiguration configuration() {
        return MapConnectorConfiguration.builder()
                .connection(ConnectionSettings.builder().url(URL).user("SA").password(SecretValue.of("")).build())
                .property(JdbcConnector.PROP_TABLES, "Employee=EMPLOYEES")
                .build();
    }

    @Override
    protected String objectType() {
        return "Employee";
    }

    @Override
    protected CreateRequest newObject(String suffix) {
        return new CreateRequest("Employee", Identifier.of("emp-" + suffix), List.of(
                Attribute.of("DISPLAYNAME", "Employee " + suffix),
                Attribute.of("DEPARTMENT", "Sales")));
    }

    @Override
    protected List<String> verifiedAttributes(CreateRequest request) {
        return List.of("DISPLAYNAME", "DEPARTMENT");
    }

    @Override
    protected Modification sampleModification() {
        return Modification.replace("DEPARTMENT", "Marketing");
    }

    @Override
    protected SearchFilter filterMatching(CreateRequest request) {
        return Filters.eq("DISPLAYNAME", request.attribute("DISPLAYNAME").flatMap(Attribute::firstString)
                .orElseThrow());
    }

    @Test
    void deleteKeepsTheRowButHidesIt() throws Exception {
        Identifier id = Identifier.of("emp-soft");
        kit().create(newObject("soft"));
        assertTrue(kit().read("Employee", id).isPresent());

        kit().delete("Employee", id);

        assertFalse(kit().read("Employee", id).isPresent());
        assertTrue(kit().searchAll(SearchRequest.of("Employee", null)).stream().noneMatch(o -> o.identifier().value().equals("emp-soft")));
        try (Connection c = DriverManager.getConnection(URL, "SA", ""); Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT ACTIVE FROM EMPLOYEES WHERE ID = 'emp-soft'")) {
            assertTrue(rs.next(), "row must still exist");
            assertFalse(rs.getBoolean(1));
        }
        ConnectorException update = assertThrows(ConnectorException.class,
                () -> kit().update("Employee", id, Modification.replace("DEPARTMENT", "x")));
        assertEquals(ErrorCode.NOT_FOUND, update.getErrorCode());
        ConnectorException again = assertThrows(ConnectorException.class, () -> kit().delete("Employee", id));
        assertEquals(ErrorCode.NOT_FOUND, again.getErrorCode());
    }
}
