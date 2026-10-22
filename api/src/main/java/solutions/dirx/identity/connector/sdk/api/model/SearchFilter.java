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

/**
 * A search filter, modeled as an immutable, sealed syntax tree.
 *
 * <p>Connectors translate filters into the query language of their target, typically with
 * a pattern-matching {@code switch}:
 * <pre>{@code
 * String toQuery(SearchFilter f) {
 *     return switch (f) {
 *         case SearchFilter.Equals e -> e.attribute() + " eq \"" + e.value() + "\"";
 *         case SearchFilter.And a -> ...;
 *         default -> throw new ConnectorException(ErrorCode.INVALID_REQUEST, "unsupported: " + f);
 *     };
 * }
 * }</pre>
 * or by implementing {@link FilterTranslator}. Use {@link Filters} to create filters.
 */
public sealed interface SearchFilter {

    /**
     * Renders the filter in LDAP string representation (RFC 4515), mainly for logging.
     *
     * @return the LDAP filter string
     */
    String toLdapString();

    /**
     * Logical AND of the given filters.
     *
     * @param filters the operands, at least one
     */
    record And(List<SearchFilter> filters) implements SearchFilter {
        /** Validates and copies the operands. */
        public And {
            filters = operands(filters);
        }

        @Override
        public String toLdapString() {
            return "(&" + String.join("", filters.stream().map(SearchFilter::toLdapString).toList()) + ")";
        }
    }

    /**
     * Logical OR of the given filters.
     *
     * @param filters the operands, at least one
     */
    record Or(List<SearchFilter> filters) implements SearchFilter {
        /** Validates and copies the operands. */
        public Or {
            filters = operands(filters);
        }

        @Override
        public String toLdapString() {
            return "(|" + String.join("", filters.stream().map(SearchFilter::toLdapString).toList()) + ")";
        }
    }

    /**
     * Logical negation.
     *
     * @param filter the negated filter
     */
    record Not(SearchFilter filter) implements SearchFilter {
        /** Validates the operand. */
        public Not {
            Objects.requireNonNull(filter, "filter");
        }

        @Override
        public String toLdapString() {
            return "(!" + filter.toLdapString() + ")";
        }
    }

    /**
     * Attribute equals value.
     *
     * @param attribute the attribute name
     * @param value     the value
     */
    record Equals(String attribute, String value) implements SearchFilter {
        /** Validates the components. */
        public Equals {
            requireAttribute(attribute);
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String toLdapString() {
            return "(" + attribute + "=" + escape(value) + ")";
        }
    }

    /**
     * Attribute has at least one value.
     *
     * @param attribute the attribute name
     */
    record Present(String attribute) implements SearchFilter {
        /** Validates the components. */
        public Present {
            requireAttribute(attribute);
        }

        @Override
        public String toLdapString() {
            return "(" + attribute + "=*)";
        }
    }

    /**
     * Substring match: {@code initial*any[0]*any[1]*...*final}.
     *
     * @param attribute the attribute name
     * @param initial   the initial part, or {@code null}
     * @param any       the inner parts; {@code null} is normalized to an empty list
     * @param fin       the final part, or {@code null}
     */
    record Substring(String attribute, String initial, List<String> any, String fin) implements SearchFilter {
        /** Validates and copies the components. */
        public Substring {
            requireAttribute(attribute);
            any = any == null ? List.of() : List.copyOf(any);
            if ((initial == null || initial.isEmpty()) && any.isEmpty() && (fin == null || fin.isEmpty())) {
                throw new IllegalArgumentException("substring filter needs at least one part");
            }
        }

        @Override
        public String toLdapString() {
            StringBuilder sb = new StringBuilder("(").append(attribute).append('=');
            if (initial != null) {
                sb.append(escape(initial));
            }
            sb.append('*');
            for (String a : any) {
                sb.append(escape(a)).append('*');
            }
            if (fin != null) {
                sb.append(escape(fin));
            }
            return sb.append(')').toString();
        }
    }

    /**
     * Attribute is greater than or equal to value, in the ordering of the target.
     *
     * @param attribute the attribute name
     * @param value     the value
     */
    record GreaterOrEqual(String attribute, String value) implements SearchFilter {
        /** Validates the components. */
        public GreaterOrEqual {
            requireAttribute(attribute);
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String toLdapString() {
            return "(" + attribute + ">=" + escape(value) + ")";
        }
    }

    /**
     * Attribute is less than or equal to value, in the ordering of the target.
     *
     * @param attribute the attribute name
     * @param value     the value
     */
    record LessOrEqual(String attribute, String value) implements SearchFilter {
        /** Validates the components. */
        public LessOrEqual {
            requireAttribute(attribute);
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String toLdapString() {
            return "(" + attribute + "<=" + escape(value) + ")";
        }
    }

    /**
     * Attribute approximately matches value; the meaning is defined by the target.
     *
     * @param attribute the attribute name
     * @param value     the value
     */
    record Approx(String attribute, String value) implements SearchFilter {
        /** Validates the components. */
        public Approx {
            requireAttribute(attribute);
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String toLdapString() {
            return "(" + attribute + "~=" + escape(value) + ")";
        }
    }

    /**
     * Escapes a value according to RFC 4515.
     *
     * @param value the raw value
     * @return the escaped value
     */
    static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '*' -> sb.append("\\2a");
                case '(' -> sb.append("\\28");
                case ')' -> sb.append("\\29");
                case '\\' -> sb.append("\\5c");
                case '\0' -> sb.append("\\00");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static List<SearchFilter> operands(List<SearchFilter> filters) {
        List<SearchFilter> copy = List.copyOf(Objects.requireNonNull(filters, "filters"));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("at least one operand is required");
        }
        return copy;
    }

    private static void requireAttribute(String attribute) {
        Objects.requireNonNull(attribute, "attribute");
        if (attribute.isBlank()) {
            throw new IllegalArgumentException("attribute name must not be blank");
        }
    }
}
