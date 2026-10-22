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

package solutions.dirx.identity.connector.sdk.api.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import solutions.dirx.identity.connector.sdk.api.Connector;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorContext;

class ConnectorRegistryTest {

    /** Registered in META-INF/services of the test resources. */
    @ConnectorInfo(name = "dummy", version = "1.2", objectTypes = {"user", "group"})
    public static class DummyConnector implements Connector {
        @Override
        public void init(ConnectorContext context) {
        }

        @Override
        public void close() {
        }
    }

    @Test
    void findsRegisteredConnectorByNameAndClass() throws ConnectorException {
        ConnectorRegistry registry = ConnectorRegistry.load(getClass().getClassLoader());
        ConnectorRegistry.Descriptor d = registry.find("dummy").orElseThrow();
        assertEquals("1.2", d.version());
        assertEquals("dummy", d.displayName());
        assertEquals(List.of("user", "group"), d.objectTypes());
        assertTrue(registry.find(DummyConnector.class.getName()).isPresent());
        assertInstanceOf(DummyConnector.class, d.newInstance());
    }
}
