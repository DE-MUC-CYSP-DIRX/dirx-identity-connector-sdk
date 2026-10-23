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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * An object read from the target system.
 *
 * @param objectType the object type, for example {@code "user"}; may be {@code null}
 * @param identifier the identifier of the object, not {@code null}
 * @param attributes the attributes; {@code null} is normalized to an empty list
 */
public record ConnectorObject(@Nullable String objectType, Identifier identifier, List<Attribute> attributes) {

    /** Validates and copies the components. */
    public ConnectorObject(@Nullable String objectType, Identifier identifier, @Nullable List<Attribute> attributes) {
        Objects.requireNonNull(identifier, "identifier");
        this.objectType = objectType;
        this.identifier = identifier;
        this.attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    /**
     * Looks up an attribute by name, ignoring case.
     *
     * @param name the attribute name
     * @return the attribute, or empty
     */
    public Optional<Attribute> attribute(String name) {
        return attributes.stream().filter(a -> a.hasName(name)).findFirst();
    }

    /**
     * Returns the first value of an attribute as string.
     *
     * @param name the attribute name
     * @return the first value, or empty
     */
    public Optional<String> firstString(String name) {
        return attribute(name).flatMap(Attribute::firstString);
    }

    /**
     * Creates a builder.
     *
     * @param objectType the object type
     * @param identifier the identifier
     * @return a new builder
     */
    public static Builder builder(@Nullable String objectType, Identifier identifier) {
        return new Builder(objectType, identifier);
    }

    /** Builder for {@link ConnectorObject}. */
    public static final class Builder {
        private final String objectType;
        private final Identifier identifier;
        private final List<Attribute> attributes = new ArrayList<>();

        private Builder(@Nullable String objectType, Identifier identifier) {
            this.objectType = objectType;
            this.identifier = identifier;
        }

        /**
         * Adds an attribute. Attributes without values are skipped.
         *
         * @param name   the attribute name
         * @param values the values
         * @return this builder
         */
        public Builder attribute(String name, @Nullable Object @Nullable ... values) {
            return attribute(name, values == null ? List.of() : Arrays.asList(values));
        }

        /**
         * Adds an attribute. Attributes without values or {@code null} values are skipped.
         *
         * @param name   the attribute name
         * @param values the values; {@code null} entries are ignored
         * @return this builder
         */
        public Builder attribute(String name, @Nullable List<@Nullable ?> values) {
            if (values != null) {
                List<Object> nonNull = values.stream().filter(Objects::nonNull).map(Object.class::cast).toList();
                if (!nonNull.isEmpty()) {
                    attributes.add(new Attribute(name, nonNull));
                }
            }
            return this;
        }

        /**
         * Adds an attribute.
         *
         * @param attribute the attribute
         * @return this builder
         */
        public Builder attribute(Attribute attribute) {
            attributes.add(Objects.requireNonNull(attribute, "attribute"));
            return this;
        }

        /**
         * Builds the object.
         *
         * @return the object
         */
        public ConnectorObject build() {
            return new ConnectorObject(objectType, identifier, attributes);
        }
    }
}
