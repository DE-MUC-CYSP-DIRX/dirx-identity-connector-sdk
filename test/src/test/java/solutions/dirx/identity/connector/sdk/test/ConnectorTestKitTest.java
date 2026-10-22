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

package solutions.dirx.identity.connector.sdk.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import solutions.dirx.identity.connector.sdk.api.Connector;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.SearchRequest;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorContext;
import solutions.dirx.identity.connector.sdk.api.config.MapConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.ConnectorObject;
import solutions.dirx.identity.connector.sdk.api.model.Filters;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.api.spi.ConnectorRegistry;

class ConnectorTestKitTest {

    @Test
    void sharedStoreIsVisibleAcrossInstances() throws ConnectorException {
        var config = MapConnectorConfiguration.builder().property(InMemoryConnector.PROP_STORE, "shared").build();
        InMemoryConnector.clearStore("shared");
        try (ConnectorTestKit a = ConnectorTestKit.open(new InMemoryConnector(), config);
             ConnectorTestKit b = ConnectorTestKit.open(new InMemoryConnector(), config)) {
            a.create("user", Identifier.of("u1"), Attribute.of("cn", "One"));
            assertTrue(b.read("USER", Identifier.of("U1")).isPresent());
        } finally {
            InMemoryConnector.clearStore("shared");
        }
    }

    @Test
    void modificationsAndProjection() throws ConnectorException {
        try (ConnectorTestKit kit = ConnectorTestKit.open(new InMemoryConnector(),
                MapConnectorConfiguration.of(Map.of()))) {
            Identifier id = Identifier.of("g1");
            kit.create("group", id, Attribute.of("member", "a", "b"), Attribute.of("cn", "G"));
            kit.update("group", id, Modification.add("member", "c"), Modification.delete("member", "a"));
            assertEquals(List.of("b", "c"), kit.read("group", id).orElseThrow().attribute("member")
                    .orElseThrow().strings());
            ConnectorException e = assertThrows(ConnectorException.class,
                    () -> kit.update("group", id, Modification.add("member", "b")));
            assertEquals(ErrorCode.ATTRIBUTE_OR_VALUE_EXISTS, e.getErrorCode());
            kit.update("group", id, Modification.delete("member"));
            ConnectorObject group = kit.searchAll(SearchRequest.read("group", id)
                    .withAttributesToGet(java.util.Set.of("CN"))).getFirst();
            assertEquals(1, group.attributes().size());
            assertTrue(group.attribute("cn").isPresent());
            assertEquals(1, kit.search("group", Filters.present("cn")).size());
            assertTrue(kit.search("group", Filters.present("member")).isEmpty());
        }
    }

    @Test
    void unsupportedOperationIsReported() throws ConnectorException {
        Connector minimal = new Connector() {
            @Override
            public void init(ConnectorContext context) {
                context.logger(getClass()).log(System.Logger.Level.INFO, "hello {0}", context.instanceName());
            }

            @Override
            public void close() {
            }
        };
        try (ConnectorTestKit kit = ConnectorTestKit.open(minimal, MapConnectorConfiguration.of(Map.of()))) {
            assertFalse(kit.supports(solutions.dirx.identity.connector.sdk.api.CreateOp.class));
            ConnectorException e = assertThrows(ConnectorException.class, kit::schema);
            assertEquals(ErrorCode.UNSUPPORTED_OPERATION, e.getErrorCode());
            assertTrue(kit.context().logContains("hello test"));
        }
    }

    @Test
    void inMemoryConnectorIsDiscoverable() {
        assertTrue(ConnectorRegistry.load(getClass().getClassLoader()).find("in-memory").isPresent());
    }
}
