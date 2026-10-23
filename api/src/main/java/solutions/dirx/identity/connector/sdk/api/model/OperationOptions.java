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

package solutions.dirx.identity.connector.sdk.api.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Read-only, case-insensitive multi-value map of per-request options supplied by the host,
 * for example {@code pagesize}, {@code sortattribute} or {@code sortorder}.
 */
public final class OperationOptions {

    private static final OperationOptions EMPTY = new OperationOptions(Map.of());

    private final Map<String, List<String>> options;

    private OperationOptions(Map<String, List<String>> source) {
        TreeMap<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        source.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        this.options = Collections.unmodifiableMap(copy);
    }

    /**
     * Returns the empty options.
     *
     * @return empty options
     */
    public static OperationOptions empty() {
        return EMPTY;
    }

    /**
     * Creates options from a map.
     *
     * @param options the options
     * @return the options
     */
    public static OperationOptions of(@Nullable Map<String, List<String>> options) {
        return options == null || options.isEmpty() ? EMPTY : new OperationOptions(options);
    }

    /**
     * Creates a builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the first value of an option.
     *
     * @param name the option name, case-insensitive
     * @return the first value, or empty
     */
    public Optional<String> get(String name) {
        List<String> values = options.get(name);
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }

    /**
     * Returns all values of an option.
     *
     * @param name the option name, case-insensitive
     * @return the values, possibly empty
     */
    public List<String> getAll(String name) {
        return options.getOrDefault(name, List.of());
    }

    /**
     * Returns the option names.
     *
     * @return the names
     */
    public Set<String> names() {
        return options.keySet();
    }

    /**
     * Tells whether there are no options.
     *
     * @return {@code true} if empty
     */
    public boolean isEmpty() {
        return options.isEmpty();
    }

    /**
     * Returns the options as read-only map with case-insensitive keys.
     *
     * @return the map
     */
    public Map<String, List<String>> asMap() {
        return options;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof OperationOptions other && options.equals(other.options);
    }

    @Override
    public int hashCode() {
        return options.hashCode();
    }

    @Override
    public String toString() {
        return options.toString();
    }

    /** Builder for {@link OperationOptions}. */
    public static final class Builder {
        private final Map<String, List<String>> options = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        private Builder() {
        }

        /**
         * Adds values to an option.
         *
         * @param name   the option name
         * @param values the values
         * @return this builder
         */
        public Builder add(String name, String... values) {
            options.computeIfAbsent(name, k -> new ArrayList<>()).addAll(Arrays.asList(values));
            return this;
        }

        /**
         * Builds the options.
         *
         * @return the options
         */
        public OperationOptions build() {
            return OperationOptions.of(options);
        }
    }
}
