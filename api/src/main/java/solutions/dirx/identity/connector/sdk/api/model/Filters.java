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

/** Factory methods for {@link SearchFilter}s. */
public final class Filters {

    private Filters() {
    }

    /**
     * Creates an AND filter.
     *
     * @param filters the operands
     * @return the filter
     */
    public static SearchFilter and(SearchFilter... filters) {
        return new SearchFilter.And(Arrays.asList(filters));
    }

    /**
     * Creates an OR filter.
     *
     * @param filters the operands
     * @return the filter
     */
    public static SearchFilter or(SearchFilter... filters) {
        return new SearchFilter.Or(Arrays.asList(filters));
    }

    /**
     * Creates a NOT filter.
     *
     * @param filter the negated filter
     * @return the filter
     */
    public static SearchFilter not(SearchFilter filter) {
        return new SearchFilter.Not(filter);
    }

    /**
     * Creates an equality filter.
     *
     * @param attribute the attribute name
     * @param value     the value
     * @return the filter
     */
    public static SearchFilter eq(String attribute, String value) {
        return new SearchFilter.Equals(attribute, value);
    }

    /**
     * Creates a presence filter.
     *
     * @param attribute the attribute name
     * @return the filter
     */
    public static SearchFilter present(String attribute) {
        return new SearchFilter.Present(attribute);
    }

    /**
     * Creates a "starts with" substring filter.
     *
     * @param attribute the attribute name
     * @param prefix    the prefix
     * @return the filter
     */
    public static SearchFilter startsWith(String attribute, String prefix) {
        return new SearchFilter.Substring(attribute, prefix, List.of(), null);
    }

    /**
     * Creates an "ends with" substring filter.
     *
     * @param attribute the attribute name
     * @param suffix    the suffix
     * @return the filter
     */
    public static SearchFilter endsWith(String attribute, String suffix) {
        return new SearchFilter.Substring(attribute, null, List.of(), suffix);
    }

    /**
     * Creates a "contains" substring filter.
     *
     * @param attribute the attribute name
     * @param part      the contained part
     * @return the filter
     */
    public static SearchFilter contains(String attribute, String part) {
        return new SearchFilter.Substring(attribute, null, List.of(part), null);
    }

    /**
     * Creates a greater-or-equal filter.
     *
     * @param attribute the attribute name
     * @param value     the value
     * @return the filter
     */
    public static SearchFilter ge(String attribute, String value) {
        return new SearchFilter.GreaterOrEqual(attribute, value);
    }

    /**
     * Creates a less-or-equal filter.
     *
     * @param attribute the attribute name
     * @param value     the value
     * @return the filter
     */
    public static SearchFilter le(String attribute, String value) {
        return new SearchFilter.LessOrEqual(attribute, value);
    }

    /**
     * Creates an approximate-match filter.
     *
     * @param attribute the attribute name
     * @param value     the value
     * @return the filter
     */
    public static SearchFilter approx(String attribute, String value) {
        return new SearchFilter.Approx(attribute, value);
    }
}
