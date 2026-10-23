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

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.OperationOptions;

/**
 * Request to delete an object.
 *
 * @param objectType the object type; may be {@code null}
 * @param identifier the object to delete
 * @param options    per-request options; {@code null} is normalized to empty options
 */
public record DeleteRequest(@Nullable String objectType, Identifier identifier, OperationOptions options) {

    /** Validates the components. */
    public DeleteRequest(@Nullable String objectType, Identifier identifier, @Nullable OperationOptions options) {
        Objects.requireNonNull(identifier, "identifier");
        this.objectType = objectType;
        this.identifier = identifier;
        this.options = options == null ? OperationOptions.empty() : options;
    }

    /**
     * Creates a request without options.
     *
     * @param objectType the object type
     * @param identifier the identifier
     */
    public DeleteRequest(@Nullable String objectType, Identifier identifier) {
        this(objectType, identifier, null);
    }
}
