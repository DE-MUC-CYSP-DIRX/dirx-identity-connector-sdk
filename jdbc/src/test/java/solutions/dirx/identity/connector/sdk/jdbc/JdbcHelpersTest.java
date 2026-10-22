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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.CreateRequest;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.RetryableConnectorException;
import solutions.dirx.identity.connector.sdk.api.SearchRequest;
import solutions.dirx.identity.connector.sdk.api.UpdateRequest;
import solutions.dirx.identity.connector.sdk.api.UpdateResult;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorContext;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.Schema.AttributeInfo;
import solutions.dirx.identity.connector.sdk.api.model.ConnectorObject;
import solutions.dirx.identity.connector.sdk.api.model.Filters;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.api.model.Schema.ObjectClassInfo;

class JdbcHelpersTest {

    private JdbcConnector connector;

    @BeforeEach
    void setUp() throws ConnectorException {
        connector = new JdbcConnector();
        connector.init(ConnectorContext.of("jdbc-test", TestDatabase.configuration(TestDatabase.create()).build()));
    }

    @AfterEach
    void tearDown() {
        connector.close();
    }

    @Test
    void schemaIsDerivedFromMetadata() throws ConnectorException {
        ObjectClassInfo account = connector.schema().objectClass("Account").orElseThrow();
        AttributeInfo id = account.attribute("ID").orElseThrow();
        assertTrue(id.required());
        assertFalse(account.attribute("SERIAL").orElseThrow().updatable());
        assertFalse(account.attribute("EMAIL").orElseThrow().required());
        assertFalse(account.attribute("DISPLAYNAME").orElseThrow().required());
        assertTrue(connector.schema().objectClass("Group").isPresent());
    }

    @Test
    void valuesAreConvertedByColumnType() throws ConnectorException {
        connector.create(new CreateRequest("Account", Identifier.of("a1"), List.of(
                Attribute.of("AGE", "17"), Attribute.of("ACTIVE", "true"), Attribute.of("BALANCE", "12.50"))));
        ConnectorObject o = read("Account", "a1");
        assertEquals(17, o.attribute("AGE").orElseThrow().first().orElseThrow());
        assertEquals(Boolean.TRUE, o.attribute("ACTIVE").orElseThrow().first().orElseThrow());
        assertEquals("12.5", o.firstString("BALANCE").orElseThrow());
        assertTrue(o.firstString("CREATED").isPresent());

        ConnectorException e = assertThrows(ConnectorException.class, () -> connector.create(
                new CreateRequest("Account", Identifier.of("a2"), List.of(Attribute.of("AGE", "old")))));
        assertEquals(ErrorCode.SCHEMA_VIOLATION, e.getErrorCode());
    }

    @Test
    void replacingTheKeyRenames() throws ConnectorException {
        connector.create(new CreateRequest("Group", Identifier.of("g1"), List.of(Attribute.of("description", "d"))));
        UpdateResult result = connector.update(new UpdateRequest("Group", Identifier.of("g1"),
                List.of(Modification.replace("NAME", "g2"), Modification.delete("DESCRIPTION"))));
        assertEquals(Identifier.of("g2"), result.identifier());
        ConnectorObject o = read("Group", "g2");
        assertTrue(o.attribute("DESCRIPTION").map(Attribute::isEmpty).orElse(true));
    }

    @Test
    void duplicatesAndMissingObjectsAreMapped() throws ConnectorException {
        connector.create(new CreateRequest("Account", Identifier.of("d1"), List.of()));
        assertEquals(ErrorCode.ALREADY_EXISTS, assertThrows(ConnectorException.class, () -> connector.create(
                new CreateRequest("Account", Identifier.of("d1"), List.of()))).getErrorCode());
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(ConnectorException.class, () -> connector.update(
                new UpdateRequest("Account", Identifier.of("nope"), List.of(Modification.replace("AGE", 1)))))
                .getErrorCode());
        assertEquals(ErrorCode.INVALID_REQUEST, assertThrows(ConnectorException.class, () -> connector.create(
                new CreateRequest("Unknown", Identifier.of("x"), List.of()))).getErrorCode());
        assertEquals(ErrorCode.NO_SUCH_ATTRIBUTE, assertThrows(ConnectorException.class, () -> connector.create(
                new CreateRequest("Account", Identifier.of("x"), List.of(Attribute.of("bogus", "v"))))).getErrorCode());
    }

    @Test
    void substringFilterEscapesWildcards() throws ConnectorException {
        connector.create(new CreateRequest("Account", Identifier.of("p1"), List.of(Attribute.of("GIVENNAME", "100%_off"))));
        connector.create(new CreateRequest("Account", Identifier.of("p2"), List.of(Attribute.of("GIVENNAME", "100 percent"))));
        assertEquals(List.of("p1"), ids(Filters.startsWith("givenName", "100%")));
        assertEquals(List.of("p1", "p2"), ids(Filters.startsWith("givenName", "100")));
        assertEquals(List.of("p2"), ids(Filters.not(Filters.contains("givenName", "_"))));
    }

    @Test
    void translatorProducesParameterizedSql() throws ConnectorException {
        JdbcTable table = connector.table("Account");
        SqlFragment f = new SqlFilterTranslator(table).translate(Filters.and(
                Filters.eq("displayName", "x"), Filters.or(Filters.ge("age", "3"), Filters.present("email"))));
        assertEquals("(\"DISPLAYNAME\" = ? AND (\"AGE\" >= ? OR \"EMAIL\" IS NOT NULL))", f.sql());
        assertEquals(List.of("x", 3L), f.parameters());
        assertEquals("a\\%b\\_c\\\\", SqlFilterTranslator.escapeLike("a%b_c\\"));
    }

    @Test
    void sqlExceptionsAreMapped() {
        assertEquals(ErrorCode.ALREADY_EXISTS, SqlErrorMapper.map("op", new SQLException("dup", "23505")).getErrorCode());
        assertEquals(ErrorCode.SCHEMA_VIOLATION, SqlErrorMapper.map("op", new SQLException("null", "23502")).getErrorCode());
        assertEquals(ErrorCode.INVALID_CREDENTIALS, SqlErrorMapper.map("op", new SQLException("pw", "28000")).getErrorCode());
        assertEquals(ErrorCode.PERMISSION_DENIED, SqlErrorMapper.map("op", new SQLException("acl", "42501")).getErrorCode());
        assertEquals(ErrorCode.ALREADY_EXISTS, SqlErrorMapper.map("op", new SQLException("dup", "23000", 1062)).getErrorCode());
        ConnectorException conn = SqlErrorMapper.map("op", new SQLException("down", "08001"));
        assertEquals(ErrorCode.CONNECTION_FAILED, conn.getErrorCode());
        assertInstanceOf(RetryableConnectorException.class, conn);
        assertEquals(ErrorCode.OTHER, SqlErrorMapper.map("op", new SQLException("x", "HY000", 1)).getErrorCode());
        assertInstanceOf(RetryableConnectorException.class, SqlErrorMapper.map("op", new SQLTimeoutException("slow")));
    }

    private ConnectorObject read(String type, String id) throws ConnectorException {
        List<ConnectorObject> found = new ArrayList<>();
        connector.search(SearchRequest.read(type, Identifier.of(id)), o -> {
            found.add(o);
            return true;
        });
        assertEquals(1, found.size());
        return found.getFirst();
    }

    private List<String> ids(solutions.dirx.identity.connector.sdk.api.model.SearchFilter filter) throws ConnectorException {
        List<String> ids = new ArrayList<>();
        connector.search(SearchRequest.of("Account", filter), o -> {
            ids.add(o.identifier().value());
            return true;
        });
        return ids;
    }
}
