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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import solutions.dirx.identity.connector.sdk.api.model.SecretValue;

/**
 * A simple, map-backed {@link ConnectorConfiguration} for tests and standalone use.
 *
 * <pre>{@code
 * ConnectorConfiguration cfg = MapConnectorConfiguration.builder()
 *         .property("baseUrl", "https://api.example.com")
 *         .secret("apiToken", "s3cr3t")
 *         .connection(ConnectionSettings.builder().user("svc").build())
 *         .build();
 * }</pre>
 */
public final class MapConnectorConfiguration implements ConnectorConfiguration {

    private final Map<String, List<String>> properties;
    private final Map<String, SecretValue> secrets;
    private final ConnectionSettings connection;

    private MapConnectorConfiguration(Map<String, List<String>> properties, Map<String, SecretValue> secrets,
                                      ConnectionSettings connection) {
        TreeMap<String, List<String>> p = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        properties.forEach((k, v) -> p.put(k, List.copyOf(v)));
        TreeMap<String, SecretValue> s = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        s.putAll(secrets);
        this.properties = Collections.unmodifiableMap(p);
        this.secrets = Collections.unmodifiableMap(s);
        this.connection = connection == null ? ConnectionSettings.empty() : connection;
    }

    /**
     * Creates a configuration from single-valued properties.
     *
     * @param properties the properties
     * @return the configuration
     */
    public static MapConnectorConfiguration of(Map<String, String> properties) {
        Builder b = builder();
        properties.forEach(b::property);
        return b.build();
    }

    /**
     * Creates a builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Optional<String> get(String name) {
        List<String> values = properties.get(name);
        if (values != null && !values.isEmpty() && !values.getFirst().isBlank()) {
            return Optional.of(values.getFirst());
        }
        return Optional.empty();
    }

    @Override
    public List<String> getList(String name) {
        return properties.getOrDefault(name, List.of());
    }

    @Override
    public Optional<SecretValue> getSecret(String name) {
        SecretValue secret = secrets.get(name);
        if (secret != null) {
            return Optional.of(secret);
        }
        return get(name).map(SecretValue::of);
    }

    @Override
    public Set<String> names() {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(properties.keySet());
        names.addAll(secrets.keySet());
        return Collections.unmodifiableSet(names);
    }

    @Override
    public ConnectionSettings connection() {
        return connection;
    }

    @Override
    public String toString() {
        return "MapConnectorConfiguration" + properties + ", secrets=" + secrets.keySet();
    }

    /** Builder for {@link MapConnectorConfiguration}. */
    public static final class Builder {
        private final Map<String, List<String>> properties = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        private final Map<String, SecretValue> secrets = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        private @Nullable ConnectionSettings connection;

        private Builder() {
        }

        /**
         * Sets a (possibly multi-valued) property, replacing previous values.
         *
         * @param name   the property name
         * @param values the values
         * @return this builder
         */
        public Builder property(String name, String... values) {
            Objects.requireNonNull(name, "name");
            properties.put(name, new ArrayList<>(Arrays.asList(values)));
            return this;
        }

        /**
         * Sets a secret property.
         *
         * @param name  the property name
         * @param value the clear text
         * @return this builder
         */
        public Builder secret(String name, String value) {
            secrets.put(Objects.requireNonNull(name, "name"), SecretValue.of(value));
            return this;
        }

        /**
         * Sets the connection settings.
         *
         * @param value the settings
         * @return this builder
         */
        public Builder connection(@Nullable ConnectionSettings value) {
            this.connection = value;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return the configuration
         */
        public MapConnectorConfiguration build() {
            return new MapConnectorConfiguration(properties, secrets, connection);
        }
    }
}
