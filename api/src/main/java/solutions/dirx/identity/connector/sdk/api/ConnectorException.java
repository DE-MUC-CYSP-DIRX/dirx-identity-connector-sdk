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
