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
