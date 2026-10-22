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
import java.util.List;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;

/**
 * Translates a {@link SearchFilter} into a target-specific representation, bottom-up.
 *
 * <p>Implement the node methods you support; the default implementations of the others
 * throw a {@link ConnectorException} with {@link ErrorCode#INVALID_REQUEST}.
 *
 * @param <T> the target representation, for example a query string
 */
public interface FilterTranslator<T> {

    /**
     * Translates a filter tree.
     *
     * @param filter the filter
     * @return the translated filter
     * @throws ConnectorException if a node is not supported
     */
    default T translate(SearchFilter filter) throws ConnectorException {
        return switch (filter) {
            case SearchFilter.And f -> and(translateAll(f.filters()));
            case SearchFilter.Or f -> or(translateAll(f.filters()));
            case SearchFilter.Not f -> not(translate(f.filter()));
            case SearchFilter.Equals f -> equalTo(f.attribute(), f.value());
            case SearchFilter.Present f -> present(f.attribute());
            case SearchFilter.Substring f -> substring(f.attribute(), f.initial(), f.any(), f.fin());
            case SearchFilter.GreaterOrEqual f -> greaterOrEqual(f.attribute(), f.value());
            case SearchFilter.LessOrEqual f -> lessOrEqual(f.attribute(), f.value());
            case SearchFilter.Approx f -> approx(f.attribute(), f.value());
        };
    }

    private List<T> translateAll(List<SearchFilter> filters) throws ConnectorException {
        List<T> result = new ArrayList<>(filters.size());
        for (SearchFilter f : filters) {
            result.add(translate(f));
        }
        return result;
    }

    /**
     * Translates an AND node.
     *
     * @param operands the translated operands
     * @return the translation
     * @throws ConnectorException if not supported
     */
    default T and(List<T> operands) throws ConnectorException {
        throw unsupported("and");
    }

    /**
     * Translates an OR node.
     *
     * @param operands the translated operands
     * @return the translation
     * @throws ConnectorException if not supported
     */
    default T or(List<T> operands) throws ConnectorException {
        throw unsupported("or");
    }

    /**
     * Translates a NOT node.
     *
     * @param operand the translated operand
     * @return the translation
     * @throws ConnectorException if not supported
     */
    default T not(T operand) throws ConnectorException {
        throw unsupported("not");
    }

    /**
     * Translates an equality node.
     *
     * @param attribute the attribute name
     * @param value     the value
     * @return the translation
     * @throws ConnectorException if not supported
     */
    default T equalTo(String attribute, String value) throws ConnectorException {
        throw unsupported("equality");
    }

    /**
     * Translates a presence node.
     *
     * @param attribute the attribute name
     * @return the translation
     * @throws ConnectorException if not supported
     */
    default T present(String attribute) throws ConnectorException {
        throw unsupported("present");
    }

    /**
     * Translates a substring node.
     *
     * @param attribute the attribute name
     * @param initial   the initial part, or {@code null}
     * @param any       the inner parts
     * @param fin       the final part, or {@code null}
     * @return the translation
     * @throws ConnectorException if not supported
     */
    default T substring(String attribute, String initial, List<String> any, String fin) throws ConnectorException {
        throw unsupported("substring");
    }

    /**
     * Translates a greater-or-equal node.
     *
     * @param attribute the attribute name
     * @param value     the value
     * @return the translation
     * @throws ConnectorException if not supported
     */
    default T greaterOrEqual(String attribute, String value) throws ConnectorException {
        throw unsupported("greaterOrEqual");
    }

    /**
     * Translates a less-or-equal node.
     *
     * @param attribute the attribute name
     * @param value     the value
     * @return the translation
     * @throws ConnectorException if not supported
     */
    default T lessOrEqual(String attribute, String value) throws ConnectorException {
        throw unsupported("lessOrEqual");
    }

    /**
     * Translates an approximate-match node.
     *
     * @param attribute the attribute name
     * @param value     the value
     * @return the translation
     * @throws ConnectorException if not supported
     */
    default T approx(String attribute, String value) throws ConnectorException {
        throw unsupported("approx");
    }

    private static ConnectorException unsupported(String node) {
        return new ConnectorException(ErrorCode.INVALID_REQUEST, "filter type '" + node + "' is not supported");
    }
}
