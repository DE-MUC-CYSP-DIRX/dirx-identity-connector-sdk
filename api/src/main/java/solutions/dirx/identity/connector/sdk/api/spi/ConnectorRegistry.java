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

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
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
    public static ConnectorRegistry load(ClassLoader classLoader) {
        ClassLoader cl = classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
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
        public Descriptor {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            objectTypes = objectTypes == null ? List.of() : List.copyOf(objectTypes);
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
