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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A change of one attribute.
 *
 * @param operation     the kind of change, not {@code null}
 * @param attributeName the attribute to change, not blank
 * @param values        the values to add, replace or delete; {@code null} is normalized to an
 *                      empty list. A {@link Operation#DELETE} without values removes the whole
 *                      attribute, a {@link Operation#REPLACE} without values clears it.
 */
public record Modification(Operation operation, String attributeName, List<Object> values) {

    /** The kind of change. */
    public enum Operation {
        /** Adds values to the attribute. */
        ADD,
        /** Replaces all values of the attribute. */
        REPLACE,
        /** Deletes the given values, or the whole attribute if no values are given. */
        DELETE
    }

    /** Validates and copies the components. */
    public Modification {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(attributeName, "attributeName");
        if (attributeName.isBlank()) {
            throw new IllegalArgumentException("attribute name must not be blank");
        }
        values = values == null ? List.of() : List.copyOf(values);
        values.forEach(Attribute::checkValueType);
    }

    /**
     * Creates an {@link Operation#ADD} modification.
     *
     * @param name   the attribute name
     * @param values the values to add
     * @return the modification
     */
    public static Modification add(String name, Object... values) {
        return new Modification(Operation.ADD, name, Arrays.asList(values));
    }

    /**
     * Creates a {@link Operation#REPLACE} modification.
     *
     * @param name   the attribute name
     * @param values the new values
     * @return the modification
     */
    public static Modification replace(String name, Object... values) {
        return new Modification(Operation.REPLACE, name, Arrays.asList(values));
    }

    /**
     * Creates a {@link Operation#DELETE} modification.
     *
     * @param name   the attribute name
     * @param values the values to delete; none to delete the whole attribute
     * @return the modification
     */
    public static Modification delete(String name, Object... values) {
        return new Modification(Operation.DELETE, name, Arrays.asList(values));
    }

    /**
     * Tells whether this modification targets the given attribute, ignoring case.
     *
     * @param name the attribute name
     * @return {@code true} if the names are equal ignoring case
     */
    public boolean hasName(String name) {
        return attributeName.equalsIgnoreCase(name);
    }

    /**
     * Returns the values as an {@link Attribute}.
     *
     * @return the attribute
     */
    public Attribute asAttribute() {
        return new Attribute(attributeName, values);
    }
}
