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
