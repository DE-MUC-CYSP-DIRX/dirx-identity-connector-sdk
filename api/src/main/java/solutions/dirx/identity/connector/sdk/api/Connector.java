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

import solutions.dirx.identity.connector.sdk.api.config.ConnectorContext;

/**
 * Base interface of every connector.
 *
 * <p>A connector additionally implements the capability interfaces of the operations it
 * supports: {@link CreateOp}, {@link UpdateOp}, {@link DeleteOp}, {@link SearchOp},
 * {@link SchemaOp} and {@link TestOp}.
 *
 * <h2>Lifecycle and threading</h2>
 * <ul>
 *   <li>The host instantiates the connector through its <b>public no-argument constructor</b>.</li>
 *   <li>It calls {@link #init(ConnectorContext)} exactly once, then any number of operations,
 *       and finally {@link #close()}.</li>
 *   <li>An instance is used by <b>one thread at a time</b>; multi-threaded hosts create one
 *       instance per worker thread. Connectors therefore need no internal synchronization.</li>
 * </ul>
 *
 * <h2>Registration</h2>
 * Register the class in {@code META-INF/services/solutions.dirx.identity.connector.sdk.api.Connector}
 * and annotate it with {@link solutions.dirx.identity.connector.sdk.api.spi.ConnectorInfo}.
 */
public interface Connector extends AutoCloseable {

    /**
     * Initializes the connector. Validate the configuration here and fail fast.
     *
     * @param context the context provided by the host
     * @throws ConnectorException with {@link ErrorCode#CONFIGURATION} for invalid configuration,
     *                            or a {@link RetryableConnectorException} if the target is
     *                            temporarily unreachable
     */
    void init(ConnectorContext context) throws ConnectorException;

    /** Releases all resources. Must not throw; failures should be logged. */
    @Override
    void close();
}
