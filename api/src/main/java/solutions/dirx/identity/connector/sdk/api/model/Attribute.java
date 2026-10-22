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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A named, possibly multi-valued attribute.
 *
 * <p>Supported value types are {@link String}, {@code byte[]}, {@link Boolean},
 * {@link Integer}, {@link Long} and {@link SecretValue}. Attribute names are compared
 * case-insensitively by {@link #hasName(String)} and by all lookups of this SDK; the record
 * {@code equals} method however compares names exactly. {@code byte[]} values are compared by
 * content in {@link #equals(Object)}.
 *
 * @param name   the attribute name, not blank
 * @param values the values; {@code null} is normalized to an empty list
 */
public record Attribute(String name, List<Object> values) {

    /** Validates and copies the components. */
    public Attribute {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("attribute name must not be blank");
        }
        values = values == null ? List.of() : List.copyOf(values);
        values.forEach(Attribute::checkValueType);
    }

    /**
     * Creates an attribute.
     *
     * @param name   the attribute name
     * @param values the values
     * @return the attribute
     */
    public static Attribute of(String name, Object... values) {
        return new Attribute(name, values == null ? List.of() : Arrays.asList(values));
    }

    /**
     * Checks that a value has a supported type.
     *
     * @param value the value
     * @throws IllegalArgumentException if the type is not supported
     */
    public static void checkValueType(Object value) {
        Objects.requireNonNull(value, "attribute values must not be null");
        if (!(value instanceof String || value instanceof byte[] || value instanceof Boolean
                || value instanceof Integer || value instanceof Long || value instanceof SecretValue)) {
            throw new IllegalArgumentException("unsupported attribute value type: " + value.getClass().getName());
        }
    }

    /**
     * Tells whether this attribute has the given name, ignoring case.
     *
     * @param otherName the name to compare with
     * @return {@code true} if the names are equal ignoring case
     */
    public boolean hasName(String otherName) {
        return name.equalsIgnoreCase(otherName);
    }

    /**
     * Tells whether the attribute has no values.
     *
     * @return {@code true} if there are no values
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * Returns the first value.
     *
     * @return the first value, or empty
     */
    public Optional<Object> first() {
        return values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }

    /**
     * Returns the first value converted with {@link #asString(Object)}.
     *
     * @return the first value as string, or empty
     */
    public Optional<String> firstString() {
        return first().map(Attribute::asString);
    }

    /**
     * Returns all values converted with {@link #asString(Object)}.
     *
     * @return the values as strings
     */
    public List<String> strings() {
        return values.stream().map(Attribute::asString).toList();
    }

    /**
     * Converts a value to a string. {@code byte[]} values are decoded as UTF-8 and secrets are
     * revealed.
     *
     * @param value the value
     * @return the string representation
     */
    public static String asString(Object value) {
        return switch (value) {
            case String s -> s;
            case byte[] b -> new String(b, java.nio.charset.StandardCharsets.UTF_8);
            case SecretValue s -> s.reveal();
            default -> String.valueOf(value);
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Attribute other) || !name.equals(other.name) || values.size() != other.values.size()) {
            return false;
        }
        for (int i = 0; i < values.size(); i++) {
            if (!Objects.deepEquals(values.get(i), other.values.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = name.hashCode();
        for (Object v : values) {
            h = 31 * h + (v instanceof byte[] b ? Arrays.hashCode(b) : v.hashCode());
        }
        return h;
    }

    @Override
    public String toString() {
        return name + "=" + values.stream()
                .map(v -> v instanceof byte[] b ? "<" + b.length + " bytes>" : String.valueOf(v))
                .toList();
    }
}
