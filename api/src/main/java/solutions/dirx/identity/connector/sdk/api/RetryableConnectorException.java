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
