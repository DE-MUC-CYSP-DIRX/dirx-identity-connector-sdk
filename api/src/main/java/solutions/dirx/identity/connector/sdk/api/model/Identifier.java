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

package solutions.dirx.identity.connector.sdk.api.model;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Identifies an object in the target system.
 *
 * @param value the identifier value, not {@code null}
 * @param type  the identifier type; {@code null} is normalized to {@link IdentifierType#GENERIC}
 */
public record Identifier(String value, IdentifierType type) {

    /** Validates and normalizes the components. */
    public Identifier(String value, @Nullable IdentifierType type) {
        Objects.requireNonNull(value, "value");
        this.value = value;
        this.type = type == null ? IdentifierType.GENERIC : type;
    }

    /**
     * Creates a {@link IdentifierType#GENERIC} identifier.
     *
     * @param value the identifier value
     * @return the identifier
     */
    public static Identifier of(String value) {
        return new Identifier(value, IdentifierType.GENERIC);
    }

    /**
     * Creates a {@link IdentifierType#DN} identifier.
     *
     * @param dn the distinguished name
     * @return the identifier
     */
    public static Identifier dn(String dn) {
        return new Identifier(dn, IdentifierType.DN);
    }

    @Override
    public String toString() {
        return type + ":" + value;
    }
}
