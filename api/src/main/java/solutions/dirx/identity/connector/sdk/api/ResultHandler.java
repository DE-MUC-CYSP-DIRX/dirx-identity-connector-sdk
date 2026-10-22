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

import solutions.dirx.identity.connector.sdk.api.model.ConnectorObject;

/** Receives the objects found by {@link SearchOp#search}. */
@FunctionalInterface
public interface ResultHandler {

    /**
     * Handles one object.
     *
     * @param object the found object
     * @return {@code true} to continue, {@code false} to stop the search
     * @throws ConnectorException to abort the search with an error
     */
    boolean handle(ConnectorObject object) throws ConnectorException;
}
