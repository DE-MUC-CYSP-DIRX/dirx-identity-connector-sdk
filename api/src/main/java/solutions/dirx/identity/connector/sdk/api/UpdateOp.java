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

/** Capability: modify objects. */
public interface UpdateOp {

    /**
     * Applies attribute modifications to an object.
     *
     * @param request the request
     * @return the result
     * @throws ConnectorException on failure, e.g. {@link ErrorCode#NOT_FOUND}
     */
    UpdateResult update(UpdateRequest request) throws ConnectorException;
}
