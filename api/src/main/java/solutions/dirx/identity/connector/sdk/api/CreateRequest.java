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
import java.util.Optional;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.OperationOptions;

/**
 * Request to create an object.
 *
 * @param objectType the object type, for example {@code "user"}; may be {@code null} if the
 *                   connector supports only one type
 * @param identifier the requested identifier; connectors may assign a different one and report
 *                   it in {@link CreateResult}
 * @param attributes the attributes of the new object
 * @param options    per-request options; {@code null} is normalized to empty options
 */
public record CreateRequest(String objectType, Identifier identifier, List<Attribute> attributes,
                            OperationOptions options) {

    /** Validates and copies the components. */
    public CreateRequest {
        Objects.requireNonNull(identifier, "identifier");
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        options = options == null ? OperationOptions.empty() : options;
    }

    /**
     * Creates a request without options.
     *
     * @param objectType the object type
     * @param identifier the identifier
     * @param attributes the attributes
     */
    public CreateRequest(String objectType, Identifier identifier, List<Attribute> attributes) {
        this(objectType, identifier, attributes, null);
    }

    /**
     * Looks up an attribute, ignoring case.
     *
     * @param name the attribute name
     * @return the attribute, or empty
     */
    public Optional<Attribute> attribute(String name) {
        return attributes.stream().filter(a -> a.hasName(name)).findFirst();
    }
}
