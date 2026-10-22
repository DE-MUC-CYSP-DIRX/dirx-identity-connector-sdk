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

package solutions.dirx.identity.connector.sdk.api.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An object read from the target system.
 *
 * @param objectType the object type, for example {@code "user"}; may be {@code null}
 * @param identifier the identifier of the object, not {@code null}
 * @param attributes the attributes; {@code null} is normalized to an empty list
 */
public record ConnectorObject(String objectType, Identifier identifier, List<Attribute> attributes) {

    /** Validates and copies the components. */
    public ConnectorObject {
        Objects.requireNonNull(identifier, "identifier");
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
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
    public static Builder builder(String objectType, Identifier identifier) {
        return new Builder(objectType, identifier);
    }

    /** Builder for {@link ConnectorObject}. */
    public static final class Builder {
        private final String objectType;
        private final Identifier identifier;
        private final List<Attribute> attributes = new ArrayList<>();

        private Builder(String objectType, Identifier identifier) {
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
        public Builder attribute(String name, Object... values) {
            return attribute(name, values == null ? List.of() : Arrays.asList(values));
        }

        /**
         * Adds an attribute. Attributes without values or {@code null} values are skipped.
         *
         * @param name   the attribute name
         * @param values the values; {@code null} entries are ignored
         * @return this builder
         */
        public Builder attribute(String name, List<?> values) {
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
