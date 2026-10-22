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
import java.util.Objects;

/** A failure reported by a connector. */
public class ConnectorException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    /**
     * Creates an exception.
     *
     * @param errorCode the error classification, not {@code null}
     * @param message   the message
     */
    public ConnectorException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    /**
     * Creates an exception.
     *
     * @param errorCode the error classification, not {@code null}
     * @param message   the message
     * @param cause     the cause, or {@code null}
     */
    public ConnectorException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    /**
     * Returns the error classification.
     *
     * @return the error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Tells whether the host may retry the operation later.
     *
     * @return {@code false}; overridden by {@link RetryableConnectorException}
     */
    public boolean isRetryable() {
        return false;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + errorCode + "]: " + getMessage();
    }
}
