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

package solutions.dirx.identity.connector.sdk.api.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelTest {

    @Test
    void attributeValidatesValueTypes() {
        assertThrows(IllegalArgumentException.class, () -> Attribute.of("x", 1.5d));
        assertThrows(NullPointerException.class, () -> Attribute.of("x", (Object) null));
        assertThrows(IllegalArgumentException.class, () -> Attribute.of(" ", "v"));
        Attribute a = Attribute.of("Mail", "a@b", 7L, true, "x".getBytes(StandardCharsets.UTF_8));
        assertTrue(a.hasName("mail"));
        assertEquals(List.of("a@b", "7", "true", "x"), a.strings());
    }

    @Test
    void attributeComparesBinaryValuesByContent() {
        assertEquals(Attribute.of("b", (Object) new byte[] {1, 2}), Attribute.of("b", (Object) new byte[] {1, 2}));
        assertEquals(Attribute.of("b", (Object) new byte[] {1, 2}).hashCode(),
                Attribute.of("b", (Object) new byte[] {1, 2}).hashCode());
        assertNotEquals(Attribute.of("b", (Object) new byte[] {1, 2}), Attribute.of("b", (Object) new byte[] {1, 3}));
    }

    @Test
    void secretValueIsMaskedAndClearable() {
        SecretValue s = SecretValue.of("pa$$");
        assertEquals("****", s.toString());
        assertEquals("pa$$", s.reveal());
        assertArrayEquals("pa$$".getBytes(StandardCharsets.UTF_8), s.revealUtf8());
        assertEquals(SecretValue.of("pa$$"), s);
        assertEquals("pw=[****]", Attribute.of("pw", s).toString());
        s.clear();
        assertTrue(s.isCleared());
        assertThrows(IllegalStateException.class, s::reveal);
    }

    @Test
    void connectorObjectLooksUpCaseInsensitively() {
        ConnectorObject o = ConnectorObject.builder("user", Identifier.of("42"))
                .attribute("givenName", "Ann")
                .attribute("empty")
                .attribute("withNull", java.util.Arrays.asList("x", null))
                .build();
        assertEquals("Ann", o.firstString("GIVENNAME").orElseThrow());
        assertFalse(o.attribute("empty").isPresent());
        assertEquals(List.of("x"), o.attribute("withnull").orElseThrow().strings());
    }

    @Test
    void operationOptionsAreCaseInsensitive() {
        OperationOptions o = OperationOptions.builder().add("PageSize", "10").add("sort", "a", "b").build();
        assertEquals("10", o.get("pagesize").orElseThrow());
        assertEquals(List.of("a", "b"), o.getAll("SORT"));
        assertTrue(OperationOptions.empty().isEmpty());
    }

    @Test
    void modificationFactories() {
        Modification m = Modification.delete("member");
        assertEquals(Modification.Operation.DELETE, m.operation());
        assertTrue(m.values().isEmpty());
        assertTrue(Modification.replace("pw", SecretValue.of("x")).hasName("PW"));
    }

    @Test
    void schemaLookups() {
        Schema s = Schema.of(Schema.ObjectClassInfo.of("user",
                Schema.AttributeInfo.of("id", Schema.AttributeType.STRING).asReadOnly(),
                Schema.AttributeInfo.of("password", Schema.AttributeType.SECRET)));
        Schema.AttributeInfo id = s.objectClass("USER").orElseThrow().attribute("ID").orElseThrow();
        assertFalse(id.creatable());
        assertFalse(s.objectClass("user").orElseThrow().attribute("password").orElseThrow().readable());
    }
}
