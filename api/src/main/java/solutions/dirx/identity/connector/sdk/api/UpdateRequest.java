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

import java.util.List;
import java.util.Objects;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.api.model.OperationOptions;

/**
 * Request to modify an object.
 *
 * <p>Password changes are ordinary {@link Modification.Operation#REPLACE} modifications whose
 * values are {@link solutions.dirx.identity.connector.sdk.api.model.SecretValue}s.
 *
 * @param objectType    the object type; may be {@code null}
 * @param identifier    the object to modify
 * @param modifications the modifications, applied in order
 * @param options       per-request options; {@code null} is normalized to empty options
 */
public record UpdateRequest(String objectType, Identifier identifier, List<Modification> modifications,
                            OperationOptions options) {

    /** Validates and copies the components. */
    public UpdateRequest {
        Objects.requireNonNull(identifier, "identifier");
        modifications = modifications == null ? List.of() : List.copyOf(modifications);
        options = options == null ? OperationOptions.empty() : options;
    }

    /**
     * Creates a request without options.
     *
     * @param objectType    the object type
     * @param identifier    the identifier
     * @param modifications the modifications
     */
    public UpdateRequest(String objectType, Identifier identifier, List<Modification> modifications) {
        this(objectType, identifier, modifications, null);
    }
}
