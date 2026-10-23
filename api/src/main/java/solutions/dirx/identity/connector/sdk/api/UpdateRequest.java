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
import org.jspecify.annotations.Nullable;
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
public record UpdateRequest(@Nullable String objectType, Identifier identifier, List<Modification> modifications,
                            OperationOptions options) {

    /** Validates and copies the components. */
    public UpdateRequest(@Nullable String objectType, Identifier identifier,
                         @Nullable List<Modification> modifications, @Nullable OperationOptions options) {
        Objects.requireNonNull(identifier, "identifier");
        this.objectType = objectType;
        this.identifier = identifier;
        this.modifications = modifications == null ? List.of() : List.copyOf(modifications);
        this.options = options == null ? OperationOptions.empty() : options;
    }

    /**
     * Creates a request without options.
     *
     * @param objectType    the object type
     * @param identifier    the identifier
     * @param modifications the modifications
     */
    public UpdateRequest(@Nullable String objectType, Identifier identifier,
                         @Nullable List<Modification> modifications) {
        this(objectType, identifier, modifications, null);
    }
}
