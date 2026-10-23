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

package solutions.dirx.identity.connector.sdk.api;

import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
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
public record SearchRequest(@Nullable String objectType, @Nullable Identifier base, SearchScope scope,
                            @Nullable SearchFilter filter, Set<String> attributesToGet, int pageSize,
                            @Nullable String pageCookie,
                            OperationOptions options) {

    /** Validates and normalizes the components. */
    public SearchRequest(@Nullable String objectType, @Nullable Identifier base, @Nullable SearchScope scope,
                         @Nullable SearchFilter filter, @Nullable Set<String> attributesToGet, int pageSize,
                         @Nullable String pageCookie, @Nullable OperationOptions options) {
        this.objectType = objectType;
        this.base = base;
        this.scope = scope == null ? SearchScope.SUBTREE : scope;
        if (pageSize < 0) {
            throw new IllegalArgumentException("pageSize must not be negative");
        }
        TreeSet<String> attrs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (attributesToGet != null) {
            attrs.addAll(attributesToGet);
        }
        this.filter = filter;
        this.attributesToGet = java.util.Collections.unmodifiableSet(attrs);
        this.pageSize = pageSize;
        this.pageCookie = pageCookie;
        this.options = options == null ? OperationOptions.empty() : options;
    }

    /**
     * Creates an unpaged subtree search over the whole target.
     *
     * @param objectType the object type
     * @param filter     the filter, or {@code null} for all objects
     * @return the request
     */
    public static SearchRequest of(@Nullable String objectType, @Nullable SearchFilter filter) {
        return new SearchRequest(objectType, null, SearchScope.SUBTREE, filter, Set.of(), 0, null, null);
    }

    /**
     * Creates a request that reads one object.
     *
     * @param objectType the object type
     * @param identifier the object to read
     * @return the request
     */
    public static SearchRequest read(@Nullable String objectType, Identifier identifier) {
        return new SearchRequest(objectType, identifier, SearchScope.OBJECT, null, Set.of(), 0, null, null);
    }

    /**
     * Returns a copy requesting the given page.
     *
     * @param size   the page size
     * @param cookie the page cookie, or {@code null} for the first page
     * @return the copy
     */
    public SearchRequest withPage(int size, @Nullable String cookie) {
        return new SearchRequest(objectType, base, scope, filter, attributesToGet, size, cookie, options);
    }

    /**
     * Returns a copy with the given attributes to return.
     *
     * @param attributes the attribute names
     * @return the copy
     */
    public SearchRequest withAttributesToGet(@Nullable Set<String> attributes) {
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
