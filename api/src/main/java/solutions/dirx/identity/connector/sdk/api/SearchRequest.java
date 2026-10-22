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

package solutions.dirx.identity.connector.sdk.api;

import java.util.Set;
import java.util.TreeSet;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.OperationOptions;
import solutions.dirx.identity.connector.sdk.api.model.SearchFilter;
import solutions.dirx.identity.connector.sdk.api.model.SearchScope;

/**
 * Request to search objects.
 *
 * @param objectType      the object type; may be {@code null}
 * @param base            the search base; {@code null} means the whole target
 * @param scope           the search scope; {@code null} is normalized to {@link SearchScope#SUBTREE}
 * @param filter          the filter; {@code null} matches all objects
 * @param attributesToGet the attributes to return (case-insensitive); empty means the
 *                        connector's default set
 * @param pageSize        the requested page size; {@code 0} means unpaged
 * @param pageCookie      the cookie returned by the previous page, or {@code null} for the first page
 * @param options         per-request options; {@code null} is normalized to empty options
 */
public record SearchRequest(String objectType, Identifier base, SearchScope scope, SearchFilter filter,
                            Set<String> attributesToGet, int pageSize, String pageCookie,
                            OperationOptions options) {

    /** Validates and normalizes the components. */
    public SearchRequest {
        scope = scope == null ? SearchScope.SUBTREE : scope;
        if (pageSize < 0) {
            throw new IllegalArgumentException("pageSize must not be negative");
        }
        TreeSet<String> attrs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (attributesToGet != null) {
            attrs.addAll(attributesToGet);
        }
        attributesToGet = java.util.Collections.unmodifiableSet(attrs);
        options = options == null ? OperationOptions.empty() : options;
    }

    /**
     * Creates an unpaged subtree search over the whole target.
     *
     * @param objectType the object type
     * @param filter     the filter, or {@code null} for all objects
     * @return the request
     */
    public static SearchRequest of(String objectType, SearchFilter filter) {
        return new SearchRequest(objectType, null, SearchScope.SUBTREE, filter, Set.of(), 0, null, null);
    }

    /**
     * Creates a request that reads one object.
     *
     * @param objectType the object type
     * @param identifier the object to read
     * @return the request
     */
    public static SearchRequest read(String objectType, Identifier identifier) {
        return new SearchRequest(objectType, identifier, SearchScope.OBJECT, null, Set.of(), 0, null, null);
    }

    /**
     * Returns a copy requesting the given page.
     *
     * @param size   the page size
     * @param cookie the page cookie, or {@code null} for the first page
     * @return the copy
     */
    public SearchRequest withPage(int size, String cookie) {
        return new SearchRequest(objectType, base, scope, filter, attributesToGet, size, cookie, options);
    }

    /**
     * Returns a copy with the given attributes to return.
     *
     * @param attributes the attribute names
     * @return the copy
     */
    public SearchRequest withAttributesToGet(Set<String> attributes) {
        return new SearchRequest(objectType, base, scope, filter, attributes, pageSize, pageCookie, options);
    }

    /**
     * Tells whether an attribute is requested, ignoring case. If no attributes are requested
     * explicitly, every attribute counts as requested.
     *
     * @param name the attribute name
     * @return {@code true} if the attribute should be returned
     */
    public boolean isRequested(String name) {
        return attributesToGet.isEmpty() || attributesToGet.contains(name);
    }
}
