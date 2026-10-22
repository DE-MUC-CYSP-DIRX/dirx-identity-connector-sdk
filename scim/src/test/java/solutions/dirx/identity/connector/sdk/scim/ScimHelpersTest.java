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

package solutions.dirx.identity.connector.sdk.scim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.RetryableConnectorException;
import solutions.dirx.identity.connector.sdk.api.model.Filters;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.rest.RestResponse;
import tools.jackson.databind.JsonNode;

class ScimHelpersTest {

    private final ScimFilterTranslator translator = new ScimFilterTranslator(Set.of("active")::contains);

    @Test
    void translatesFilters() throws ConnectorException {
        assertEquals("userName eq \"a\\\"b\"", translator.translate(Filters.eq("userName", "a\"b")));
        assertEquals("active eq true", translator.translate(Filters.eq("active", "true")));
        assertEquals("(userName sw \"j\" and not(title pr))",
                translator.translate(Filters.and(Filters.startsWith("userName", "j"), Filters.not(Filters.present("title")))));
        assertEquals("(emails.value ew \"@x.com\" or displayName co \"x\")",
                translator.translate(Filters.or(Filters.endsWith("emails.value", "@x.com"), Filters.contains("displayName", "x"))));
        assertEquals("meta.lastModified ge \"2024-01-01\"", translator.translate(Filters.ge("meta.lastModified", "2024-01-01")));
        ConnectorException e = assertThrows(ConnectorException.class, () -> translator.translate(Filters.approx("a", "b")));
        assertEquals(ErrorCode.INVALID_REQUEST, e.getErrorCode());
    }

    @Test
    void buildsPatchOperations() {
        JsonNode body = ScimPatch.build(List.of(
                Modification.replace("displayName", "New"),
                Modification.replace("title"),
                Modification.add("emails", "{\"value\":\"a@x.com\",\"type\":\"work\"}"),
                Modification.delete("members", "1", "2"),
                Modification.delete("nickName", "x")), Set.of("emails", "members")::contains);

        assertEquals(ScimPatch.PATCH_OP_SCHEMA, body.path("schemas").get(0).asString());
        JsonNode ops = body.path("Operations");
        assertEquals(6, ops.size());
        assertEquals("replace", ops.get(0).path("op").asString());
        assertEquals("New", ops.get(0).path("value").asString());
        assertEquals("remove", ops.get(1).path("op").asString());
        assertEquals("title", ops.get(1).path("path").asString());
        assertTrue(ops.get(2).path("value").isArray());
        assertEquals("work", ops.get(2).path("value").get(0).path("type").asString());
        assertEquals("members[value eq \"1\"]", ops.get(3).path("path").asString());
        assertEquals("members[value eq \"2\"]", ops.get(4).path("path").asString());
        assertEquals("nickName", ops.get(5).path("path").asString());
        assertFalse(ops.get(5).has("value"));
    }

    @Test
    void mapsScimErrorTypes() {
        assertEquals(ErrorCode.ALREADY_EXISTS, map(409, "{\"scimType\":\"uniqueness\"}").getErrorCode());
        assertEquals(ErrorCode.SCHEMA_VIOLATION, map(400, "{\"scimType\":\"mutability\"}").getErrorCode());
        assertEquals(ErrorCode.NO_SUCH_ATTRIBUTE, map(400, "{\"scimType\":\"noTarget\"}").getErrorCode());
        assertEquals(ErrorCode.INVALID_REQUEST, map(400, "{\"scimType\":\"invalidFilter\"}").getErrorCode());
        assertEquals(ErrorCode.NOT_FOUND, map(404, "not json").getErrorCode());
        assertInstanceOf(RetryableConnectorException.class, map(503, "{\"scimType\":\"uniqueness\"}"));
    }

    private static ConnectorException map(int status, String body) {
        RestResponse response = new RestResponse(status, HttpHeaders.of(Map.of(), (a, b) -> true), body);
        return ScimErrorMapper.INSTANCE.map("POST", URI.create("http://localhost/Users"), response);
    }
}
