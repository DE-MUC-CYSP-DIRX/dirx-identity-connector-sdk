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

/**
 * Result of {@link SearchOp#search}.
 *
 * @param nextPageCookie the cookie to request the next page, or {@code null} if there are no
 *                       more pages
 */
public record SearchResult(String nextPageCookie) {

    private static final SearchResult DONE = new SearchResult(null);

    /**
     * Returns a result indicating that there are no more pages.
     *
     * @return the result
     */
    public static SearchResult done() {
        return DONE;
    }

    /**
     * Returns a result indicating that more pages are available.
     *
     * @param cookie the cookie for the next page, not {@code null}
     * @return the result
     */
    public static SearchResult nextPage(String cookie) {
        return new SearchResult(java.util.Objects.requireNonNull(cookie, "cookie"));
    }

    /**
     * Tells whether more pages are available.
     *
     * @return {@code true} if {@link #nextPageCookie()} is set
     */
    public boolean hasMorePages() {
        return nextPageCookie != null;
    }
}
