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
