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

package solutions.dirx.identity.connector.sdk.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.model.SecretValue;

class ConfigurationTest {

    private final MapConnectorConfiguration cfg = MapConnectorConfiguration.builder()
            .property("BaseUrl", "https://x")
            .property("timeout", "PT5S")
            .property("timeoutMillis", "250")
            .property("retries", "3")
            .property("debug", "yes")
            .property("scopes", "a", "b")
            .property("blank", " ")
            .property("bad", "abc")
            .secret("token", "t0k")
            .connection(ConnectionSettings.builder().user("svc").password(SecretValue.of("pw")).build())
            .build();

    @Test
    void typedAccessors() throws ConnectorException {
        assertEquals("https://x", cfg.require("baseurl"));
        assertEquals(Duration.ofSeconds(5), cfg.getDuration("timeout", null));
        assertEquals(Duration.ofMillis(250), cfg.getDuration("timeoutMillis", null));
        assertEquals(3, cfg.getInt("retries", 1));
        assertEquals(1, cfg.getInt("missing", 1));
        assertTrue(cfg.getBoolean("debug", false));
        assertEquals(List.of("a", "b"), cfg.getList("SCOPES"));
        assertFalse(cfg.get("blank").isPresent());
        assertEquals("t0k", cfg.getSecret("TOKEN").orElseThrow().reveal());
        assertEquals("svc", cfg.connection().userValue().orElseThrow());
        assertEquals("pw", cfg.connection().passwordValue().orElseThrow().reveal());
        assertTrue(cfg.names().contains("token"));
    }

    @Test
    void invalidValuesRaiseConfigurationErrors() {
        assertEquals(ErrorCode.CONFIGURATION, assertThrows(ConnectorException.class, () -> cfg.getInt("bad", 0)).getErrorCode());
        assertEquals(ErrorCode.CONFIGURATION, assertThrows(ConnectorException.class, () -> cfg.getBoolean("bad", false)).getErrorCode());
        assertEquals(ErrorCode.CONFIGURATION, assertThrows(ConnectorException.class, () -> cfg.getDuration("bad", null)).getErrorCode());
        assertEquals(ErrorCode.CONFIGURATION, assertThrows(ConnectorException.class, () -> cfg.require("missing")).getErrorCode());
    }

    @Test
    void secretsAreNotPrinted() {
        assertFalse(cfg.toString().contains("t0k"));
        assertFalse(cfg.connection().toString().contains("pw,"));
    }
}
