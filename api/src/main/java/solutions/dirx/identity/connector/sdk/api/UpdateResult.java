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
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;

/**
 * Result of {@link UpdateOp#update}.
 *
 * @param identifier the new identifier if the object was renamed, otherwise {@code null}
 * @param attributes attributes changed by the target as side effect; {@code null} is
 *                   normalized to an empty list
 */
public record UpdateResult(Identifier identifier, List<Attribute> attributes) {

    private static final UpdateResult UNCHANGED = new UpdateResult(null, List.of());

    /** Copies the components. */
    public UpdateResult {
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
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
