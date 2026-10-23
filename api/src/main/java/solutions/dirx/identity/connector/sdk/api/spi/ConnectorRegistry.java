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

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import org.jspecify.annotations.Nullable;
import solutions.dirx.identity.connector.sdk.api.Connector;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;

/**
 * Lists the connectors registered via {@link ServiceLoader} without instantiating them.
 *
 * <pre>{@code
 * ConnectorRegistry registry = ConnectorRegistry.load(getClass().getClassLoader());
 * Connector c = registry.find("acme-hr").orElseThrow().newInstance();
 * }</pre>
 */
public final class ConnectorRegistry {

    private final List<Descriptor> connectors;

    private ConnectorRegistry(List<Descriptor> connectors) {
        this.connectors = List.copyOf(connectors);
    }

    /**
     * Loads all connectors visible to a class loader.
     *
     * @param classLoader the class loader, or {@code null} for the thread context class loader
     * @return the registry
     */
    public static ConnectorRegistry load(@Nullable ClassLoader classLoader) {
        @Nullable ClassLoader cl = classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
        List<Descriptor> list = ServiceLoader.load(Connector.class, cl).stream()
                .map(ServiceLoader.Provider::type)
                .map(Descriptor::of)
                .toList();
        return new ConnectorRegistry(list);
    }

    /**
     * Returns all registered connectors.
     *
     * @return the descriptors
     */
    public List<Descriptor> connectors() {
        return connectors;
    }

    /**
     * Finds a connector by its {@link ConnectorInfo#name()} or by its fully qualified class name.
     *
     * @param nameOrClassName the name or class name
     * @return the descriptor, or empty
     */
    public Optional<Descriptor> find(String nameOrClassName) {
        return connectors.stream()
                .filter(d -> d.name().equals(nameOrClassName) || d.type().getName().equals(nameOrClassName))
                .findFirst();
    }

    /**
     * Describes a registered connector.
     *
     * @param name        the name from {@link ConnectorInfo}, or the class name if not annotated
     * @param version     the version
     * @param displayName the display name
     * @param description the description
     * @param objectTypes the supported object types
     * @param type        the connector class
     */
    public record Descriptor(String name, String version, String displayName, String description,
                             List<String> objectTypes, Class<? extends Connector> type) {

        /** Validates and copies the components. */
        public Descriptor(String name, String version, String displayName, String description,
                          @Nullable List<String> objectTypes, Class<? extends Connector> type) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            this.name = name;
            this.version = version;
            this.displayName = displayName;
            this.description = description;
            this.objectTypes = objectTypes == null ? List.of() : List.copyOf(objectTypes);
            this.type = type;
        }

        /**
         * Creates a descriptor from the {@link ConnectorInfo} annotation of a class.
         *
         * @param type the connector class
         * @return the descriptor
         */
        public static Descriptor of(Class<? extends Connector> type) {
            ConnectorInfo info = type.getAnnotation(ConnectorInfo.class);
            if (info == null) {
                return new Descriptor(type.getName(), "", type.getSimpleName(), "", List.of(), type);
            }
            String display = info.displayName().isEmpty() ? info.name() : info.displayName();
            return new Descriptor(info.name(), info.version(), display, info.description(),
                    List.of(info.objectTypes()), type);
        }

        /**
         * Creates a new, uninitialized connector instance.
         *
         * @return the connector
         * @throws ConnectorException with {@link ErrorCode#CONFIGURATION} if the class cannot be instantiated
         */
        public Connector newInstance() throws ConnectorException {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (InvocationTargetException e) {
                throw new ConnectorException(ErrorCode.CONFIGURATION,
                        "constructor of connector " + type.getName() + " failed", e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new ConnectorException(ErrorCode.CONFIGURATION,
                        "connector " + type.getName() + " needs a public no-argument constructor", e);
            }
        }
    }
}
