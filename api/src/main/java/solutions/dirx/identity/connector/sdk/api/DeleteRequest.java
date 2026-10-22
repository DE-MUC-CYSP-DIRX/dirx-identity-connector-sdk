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

import java.util.Objects;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.OperationOptions;

/**
 * Request to delete an object.
 *
 * @param objectType the object type; may be {@code null}
 * @param identifier the object to delete
 * @param options    per-request options; {@code null} is normalized to empty options
 */
public record DeleteRequest(String objectType, Identifier identifier, OperationOptions options) {

    /** Validates the components. */
    public DeleteRequest {
        Objects.requireNonNull(identifier, "identifier");
        options = options == null ? OperationOptions.empty() : options;
    }

    /**
     * Creates a request without options.
     *
     * @param objectType the object type
     * @param identifier the identifier
     */
    public DeleteRequest(String objectType, Identifier identifier) {
        this(objectType, identifier, null);
    }
}
