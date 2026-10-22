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

/** Capability: search objects. */
public interface SearchOp {

    /**
     * Searches objects and streams them to the handler.
     *
     * <p>If {@link SearchRequest#pageSize()} is positive and the target supports paging, return
     * at most that many objects and a {@link SearchResult#nextPageCookie()} for the next page.
     *
     * @param request the request
     * @param handler receives the found objects; stop when it returns {@code false}
     * @return the result, with a cookie if more pages are available
     * @throws ConnectorException on failure
     */
    SearchResult search(SearchRequest request, ResultHandler handler) throws ConnectorException;
}
