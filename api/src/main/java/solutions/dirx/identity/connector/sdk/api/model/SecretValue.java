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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A sensitive value such as a password or an API token.
 *
 * <p>The clear text is never part of {@link #toString()}, so secrets do not leak into logs
 * by accident. The host decrypts secrets before handing them to a connector; connectors
 * call {@link #reveal()} only at the point where the clear text is really needed.
 */
public final class SecretValue {

    private static final String MASK = "****";

    private @Nullable char[] value;

    private SecretValue(char[] value) {
        this.value = value;
    }

    /**
     * Creates a secret from a string.
     *
     * @param clearText the clear text, not {@code null}
     * @return the secret
     */
    public static SecretValue of(String clearText) {
        Objects.requireNonNull(clearText, "clearText");
        return new SecretValue(clearText.toCharArray());
    }

    /**
     * Creates a secret from a character array. The array is copied.
     *
     * @param clearText the clear text, not {@code null}
     * @return the secret
     */
    public static SecretValue of(char[] clearText) {
        Objects.requireNonNull(clearText, "clearText");
        return new SecretValue(clearText.clone());
    }

    /**
     * Returns the clear text.
     *
     * @return the clear text
     * @throws IllegalStateException if the secret has been cleared
     */
    public synchronized String reveal() {
        return new String(checked());
    }

    /**
     * Returns a copy of the clear text as character array. The caller should wipe it after use.
     *
     * @return the clear text
     * @throws IllegalStateException if the secret has been cleared
     */
    public synchronized char[] revealChars() {
        return checked().clone();
    }

    /**
     * Returns the clear text encoded as UTF-8. The caller should wipe it after use.
     *
     * @return the UTF-8 bytes of the clear text
     * @throws IllegalStateException if the secret has been cleared
     */
    public synchronized byte[] revealUtf8() {
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(checked()));
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        if (buffer.hasArray()) {
            Arrays.fill(buffer.array(), (byte) 0);
        }
        return bytes;
    }

    /** Overwrites the clear text in memory. Subsequent reveal calls fail. */
    public synchronized void clear() {
        if (value != null) {
            Arrays.fill(value, '\0');
            value = null;
        }
    }

    /**
     * Tells whether {@link #clear()} has been called.
     *
     * @return {@code true} if the secret has been cleared
     */
    public synchronized boolean isCleared() {
        return value == null;
    }

    private char[] checked() {
        if (value == null) {
            throw new IllegalStateException("secret has been cleared");
        }
        return value;
    }

    /** Compares the clear texts in constant time. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SecretValue other)) {
            return false;
        }
        byte[] a = isCleared() ? new byte[0] : revealUtf8();
        byte[] b = other.isCleared() ? new byte[0] : other.revealUtf8();
        try {
            return isCleared() == other.isCleared() && MessageDigest.isEqual(a, b);
        } finally {
            Arrays.fill(a, (byte) 0);
            Arrays.fill(b, (byte) 0);
        }
    }

    /** Returns a constant, so the hash code does not disclose anything about the secret. */
    @Override
    public int hashCode() {
        return 0x5ec7e7;
    }

    /** Always returns a mask; never the clear text. */
    @Override
    public String toString() {
        return MASK;
    }
}
