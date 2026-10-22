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

package solutions.dirx.identity.connector.sdk.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.api.model.SecretValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class JsonAndPagingTest {

    @Test
    void flattensJson() {
        JsonNode node = Json.mapper().readTree("""
                {"id":"7","userName":"jdoe","active":true,"age":42,"big":12345678901,"score":1.5,
                 "name":{"givenName":"John","familyName":null},"roles":["a","b"],"empty":[],
                 "emails":[{"value":"j@x","primary":true}]}
                """);
        List<Attribute> attrs = Json.toAttributes(node, "id");
        assertEquals(List.of(
                Attribute.of("userName", "jdoe"),
                Attribute.of("active", true),
                Attribute.of("age", 42),
                Attribute.of("big", 12345678901L),
                Attribute.of("score", "1.5"),
                Attribute.of("name.givenName", "John"),
                Attribute.of("roles", "a", "b"),
                Attribute.of("emails", "{\"value\":\"j@x\",\"primary\":true}")), attrs);
    }

    @Test
    void buildsJson() {
        ObjectNode o = Json.toObject(List.of(
                Attribute.of("userName", "jdoe"),
                Attribute.of("name.givenName", "John"),
                Attribute.of("roles", "a"),
                Attribute.of("password", SecretValue.of("pw")),
                Attribute.of("tags", "x", "y")), n -> n.equals("roles"));
        assertEquals("{\"userName\":\"jdoe\",\"name\":{\"givenName\":\"John\"},\"roles\":[\"a\"],"
                + "\"password\":\"pw\",\"tags\":[\"x\",\"y\"]}", o.toString());
    }

    @Test
    void appliesModifications() {
        ObjectNode o = (ObjectNode) Json.mapper().readTree(
                "{\"mail\":\"a@x\",\"roles\":[\"a\",\"b\"],\"name\":{\"givenName\":\"J\"}}");
        Json.apply(o, List.of(
                Modification.replace("mail", "b@x"),
                Modification.add("roles", "b", "c"),
                Modification.delete("roles", "a"),
                Modification.delete("name.givenName"),
                Modification.replace("name.familyName", "Doe")), n -> n.equals("roles"));
        assertEquals("{\"mail\":\"b@x\",\"roles\":[\"b\",\"c\"],\"name\":{\"familyName\":\"Doe\"}}", o.toString());
    }

    @Test
    void pageCookiesRoundTrip() throws Exception {
        assertEquals(40, PageCookies.offset(PageCookies.ofOffset(40), 0));
        assertEquals(1, PageCookies.offset(null, 1));
        assertEquals("https://h/next?x=1", PageCookies.token(PageCookies.ofToken("https://h/next?x=1")));
        assertNull(PageCookies.token(null));
        ConnectorException e = assertThrows(ConnectorException.class,
                () -> PageCookies.offset(PageCookies.ofToken("t"), 0));
        assertEquals(ErrorCode.INVALID_REQUEST, e.getErrorCode());
        assertThrows(ConnectorException.class, () -> PageCookies.token("%%%"));
    }
}
