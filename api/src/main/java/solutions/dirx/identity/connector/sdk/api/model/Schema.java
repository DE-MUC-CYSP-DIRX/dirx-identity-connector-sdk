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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The schema of a target system, as reported by schema discovery.
 *
 * @param objectClasses the supported object types
 */
public record Schema(List<ObjectClassInfo> objectClasses) {

    /** Copies the components. */
    public Schema {
        objectClasses = objectClasses == null ? List.of() : List.copyOf(objectClasses);
    }

    /**
     * Creates a schema.
     *
     * @param objectClasses the supported object types
     * @return the schema
     */
    public static Schema of(ObjectClassInfo... objectClasses) {
        return new Schema(List.of(objectClasses));
    }

    /**
     * Looks up an object type, ignoring case.
     *
     * @param name the object type name
     * @return the object type, or empty
     */
    public Optional<ObjectClassInfo> objectClass(String name) {
        return objectClasses.stream().filter(o -> o.name().equalsIgnoreCase(name)).findFirst();
    }

    /**
     * Describes one object type.
     *
     * @param name       the object type name, for example {@code "user"}
     * @param attributes the attributes of the type
     */
    public record ObjectClassInfo(String name, List<AttributeInfo> attributes) {

        /** Validates and copies the components. */
        public ObjectClassInfo {
            Objects.requireNonNull(name, "name");
            attributes = attributes == null ? List.of() : List.copyOf(attributes);
        }

        /**
         * Creates an object type description.
         *
         * @param name       the object type name
         * @param attributes the attributes
         * @return the description
         */
        public static ObjectClassInfo of(String name, AttributeInfo... attributes) {
            return new ObjectClassInfo(name, List.of(attributes));
        }

        /**
         * Looks up an attribute, ignoring case.
         *
         * @param attributeName the attribute name
         * @return the attribute, or empty
         */
        public Optional<AttributeInfo> attribute(String attributeName) {
            return attributes.stream().filter(a -> a.name().equalsIgnoreCase(attributeName)).findFirst();
        }
    }

    /** The value type of an attribute. */
    public enum AttributeType {
        /** {@link String} values. */
        STRING,
        /** {@code byte[]} values. */
        BINARY,
        /** {@link Boolean} values. */
        BOOLEAN,
        /** {@link Integer} values. */
        INTEGER,
        /** {@link Long} values. */
        LONG,
        /** {@link SecretValue} values, for example passwords. */
        SECRET
    }

    /**
     * Describes one attribute of an object type.
     *
     * @param name        the attribute name
     * @param type        the value type
     * @param multiValued whether more than one value is allowed
     * @param required    whether a value is required on create
     * @param creatable   whether the attribute can be set on create
     * @param updatable   whether the attribute can be modified
     * @param readable    whether the attribute is returned by searches
     */
    public record AttributeInfo(String name, AttributeType type, boolean multiValued, boolean required,
                                boolean creatable, boolean updatable, boolean readable) {

        /** Validates the components. */
        public AttributeInfo {
            Objects.requireNonNull(name, "name");
            type = type == null ? AttributeType.STRING : type;
        }

        /**
         * Creates a single-valued, optional, fully accessible attribute description.
         *
         * @param name the attribute name
         * @param type the value type
         * @return the description
         */
        public static AttributeInfo of(String name, AttributeType type) {
            return new AttributeInfo(name, type, false, false, true, true, type != AttributeType.SECRET);
        }

        /**
         * Returns a copy that is multi-valued.
         *
         * @return the copy
         */
        public AttributeInfo asMultiValued() {
            return new AttributeInfo(name, type, true, required, creatable, updatable, readable);
        }

        /**
         * Returns a copy that is required.
         *
         * @return the copy
         */
        public AttributeInfo asRequired() {
            return new AttributeInfo(name, type, multiValued, true, creatable, updatable, readable);
        }

        /**
         * Returns a copy that is read-only (neither creatable nor updatable).
         *
         * @return the copy
         */
        public AttributeInfo asReadOnly() {
            return new AttributeInfo(name, type, multiValued, required, false, false, true);
        }
    }
}
