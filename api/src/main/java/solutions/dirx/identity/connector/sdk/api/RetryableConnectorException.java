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

import java.io.Serial;

/**
 * A temporary failure, for example an unreachable target. The host may retry the operation
 * later. Thrown by {@link Connector#init} it signals that the target is currently unavailable.
 */
public class RetryableConnectorException extends ConnectorException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception.
     *
     * @param errorCode the error classification, typically {@link ErrorCode#CONNECTION_FAILED}
     *                  or {@link ErrorCode#TIMEOUT}
     * @param message   the message
     */
    public RetryableConnectorException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * Creates an exception.
     *
     * @param errorCode the error classification
     * @param message   the message
     * @param cause     the cause, or {@code null}
     */
    public RetryableConnectorException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    @Override
    public boolean isRetryable() {
        return true;
    }
}
