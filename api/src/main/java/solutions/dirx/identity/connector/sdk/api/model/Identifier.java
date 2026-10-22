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

package solutions.dirx.identity.connector.sdk.api.model;

import java.util.Objects;

/**
 * Identifies an object in the target system.
 *
 * @param value the identifier value, not {@code null}
 * @param type  the identifier type; {@code null} is normalized to {@link IdentifierType#GENERIC}
 */
public record Identifier(String value, IdentifierType type) {

    /** Validates and normalizes the components. */
    public Identifier {
        Objects.requireNonNull(value, "value");
        type = type == null ? IdentifierType.GENERIC : type;
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
