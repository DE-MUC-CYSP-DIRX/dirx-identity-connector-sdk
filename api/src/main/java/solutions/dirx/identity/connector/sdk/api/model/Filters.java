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
