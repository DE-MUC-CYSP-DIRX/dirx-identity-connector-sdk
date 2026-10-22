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
