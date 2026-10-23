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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The schema of a target system, as reported by schema discovery.
 *
 * @param objectClasses the supported object types
 */
public record Schema(List<ObjectClassInfo> objectClasses) {

    /** Copies the components. */
    public Schema(@Nullable List<ObjectClassInfo> objectClasses) {
        this.objectClasses = objectClasses == null ? List.of() : List.copyOf(objectClasses);
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
        public ObjectClassInfo(String name, @Nullable List<AttributeInfo> attributes) {
            Objects.requireNonNull(name, "name");
            this.name = name;
            this.attributes = attributes == null ? List.of() : List.copyOf(attributes);
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
        public AttributeInfo(String name, @Nullable AttributeType type, boolean multiValued, boolean required,
                             boolean creatable, boolean updatable, boolean readable) {
            Objects.requireNonNull(name, "name");
            this.name = name;
            this.type = type == null ? AttributeType.STRING : type;
            this.multiValued = multiValued;
            this.required = required;
            this.creatable = creatable;
            this.updatable = updatable;
            this.readable = readable;
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
