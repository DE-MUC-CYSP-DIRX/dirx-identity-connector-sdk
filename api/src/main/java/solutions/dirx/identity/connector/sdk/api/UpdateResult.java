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
import org.jspecify.annotations.Nullable;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;

/**
 * Result of {@link UpdateOp#update}.
 *
 * @param identifier the new identifier if the object was renamed, otherwise {@code null}
 * @param attributes attributes changed by the target as side effect; {@code null} is
 *                   normalized to an empty list
 */
public record UpdateResult(@Nullable Identifier identifier, List<Attribute> attributes) {

    private static final UpdateResult UNCHANGED = new UpdateResult(null, List.of());

    /** Copies the components. */
    public UpdateResult(@Nullable Identifier identifier, @Nullable List<Attribute> attributes) {
        this.identifier = identifier;
        this.attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    /**
     * Returns a result for an update that did not rename the object.
     *
     * @return the result
     */
    public static UpdateResult unchanged() {
        return UNCHANGED;
    }

    /**
     * Returns a result for an update that renamed the object.
     *
     * @param newIdentifier the new identifier
     * @return the result
     */
    public static UpdateResult renamed(Identifier newIdentifier) {
        return new UpdateResult(newIdentifier, List.of());
    }
}
