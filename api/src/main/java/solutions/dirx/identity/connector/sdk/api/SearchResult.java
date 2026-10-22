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
