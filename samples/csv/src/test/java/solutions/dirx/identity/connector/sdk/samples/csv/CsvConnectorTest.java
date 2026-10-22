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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.CreateRequest;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.UpdateResult;
import solutions.dirx.identity.connector.sdk.api.config.MapConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.ConnectorObject;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.test.ConnectorTestKit;

class CsvConnectorTest {

    @TempDir
    Path dir;

    @Test
    void formatRoundTripsSpecialCharacters() throws IOException {
        CsvFormat format = new CsvFormat(';');
        List<List<String>> records = List.of(
                List.of("plain", "with;separator", "with \"quotes\""),
                List.of("multi\r\nline", "", "x"));
        StringWriter out = new StringWriter();
        for (List<String> r : records) {
            format.write(out, r);
        }
        assertEquals(records, format.read(new java.io.BufferedReader(new StringReader(out.toString()))));
    }

    @Test
    void formatRejectsUnterminatedQuote() {
        CsvFormat format = new CsvFormat(',');
        assertThrows(IOException.class, () -> format.read(new java.io.BufferedReader(new StringReader("\"abc"))));
    }

    @Test
    void existingFileIsReadAndUpdated() throws Exception {
        Path file = dir.resolve("users.csv");
        Files.writeString(file, "ID;Mail;Roles\r\nalice;alice@example.com;admin|user\r\n", StandardCharsets.UTF_8);
        try (ConnectorTestKit kit = ConnectorTestKit.open(new CsvConnector(), MapConnectorConfiguration.builder()
                .property(CsvConnector.PROP_FILE, file.toString())
                .property(CsvConnector.PROP_SEPARATOR, ";")
                .property(CsvConnector.PROP_OBJECT_TYPE, "User")
                .build())) {
            ConnectorObject alice = kit.read("User", Identifier.of("ALICE")).orElseThrow();
            assertEquals(List.of("admin", "user"), alice.attribute("Roles").orElseThrow().strings());

            kit.update("User", Identifier.of("alice"), Modification.add("roles", "auditor"),
                    Modification.delete("roles", "admin"));
            assertEquals(List.of("user", "auditor"),
                    kit.read("User", Identifier.of("alice")).orElseThrow().attribute("Roles").orElseThrow().strings());

            UpdateResult renamed = kit.update("User", Identifier.of("alice"), Modification.replace("id", "alice2"));
            assertEquals(Identifier.of("alice2"), renamed.identifier());
            assertTrue(kit.read("User", Identifier.of("alice")).isEmpty());
        }
        assertTrue(Files.readString(file).startsWith("ID;Mail;Roles\r\nalice2;"));
    }

    @Test
    void errorsAreMapped() throws Exception {
        try (ConnectorTestKit kit = ConnectorTestKit.open(new CsvConnector(), MapConnectorConfiguration.builder()
                .property(CsvConnector.PROP_FILE, dir.resolve("a.csv").toString())
                .property(CsvConnector.PROP_COLUMNS, "id", "mail")
                .build())) {
            assertCode(ErrorCode.NO_SUCH_ATTRIBUTE, () -> kit.create(new CreateRequest("Account",
                    Identifier.of("x"), List.of(Attribute.of("unknown", "v")))));
            assertCode(ErrorCode.SCHEMA_VIOLATION, () -> kit.create(new CreateRequest("Account",
                    Identifier.of("x"), List.of(Attribute.of("mail", "a|b")))));
            assertCode(ErrorCode.INVALID_REQUEST, () -> kit.create(new CreateRequest("Group",
                    Identifier.of("x"), List.of())));
        }
    }

    @Test
    void missingFileWithoutColumnsIsAConfigurationError() {
        ConnectorException e = assertThrows(ConnectorException.class, () -> ConnectorTestKit.open(new CsvConnector(),
                MapConnectorConfiguration.builder().property(CsvConnector.PROP_FILE, dir.resolve("none.csv").toString())
                        .build()));
        assertEquals(ErrorCode.CONFIGURATION, e.getErrorCode());
    }

    private static void assertCode(ErrorCode expected, Call call) {
        ConnectorException e = assertThrows(ConnectorException.class, call::run);
        assertEquals(expected, e.getErrorCode());
    }

    @FunctionalInterface
    private interface Call {
        void run() throws ConnectorException;
    }
}
