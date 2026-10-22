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

package solutions.dirx.identity.connector.sdk.test;

import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import solutions.dirx.identity.connector.sdk.api.config.ConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorContext;

/**
 * A {@link ConnectorContext} for tests. It records all log messages of the connector, so
 * that tests can assert on them, for example to verify that secrets are never logged.
 */
public final class TestConnectorContext implements ConnectorContext {

    /**
     * A recorded log message.
     *
     * @param logger  the logger name
     * @param level   the level
     * @param message the formatted message
     * @param thrown  the logged exception, or {@code null}
     */
    public record LogRecord(String logger, System.Logger.Level level, String message, Throwable thrown) {
    }

    private final String instanceName;
    private final ConnectorConfiguration configuration;
    private final List<LogRecord> records = new CopyOnWriteArrayList<>();

    /**
     * Creates a context.
     *
     * @param instanceName  the instance name
     * @param configuration the configuration
     */
    public TestConnectorContext(String instanceName, ConnectorConfiguration configuration) {
        this.instanceName = Objects.requireNonNull(instanceName, "instanceName");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    @Override
    public String instanceName() {
        return instanceName;
    }

    @Override
    public ConnectorConfiguration configuration() {
        return configuration;
    }

    @Override
    public System.Logger logger(Class<?> type) {
        return new RecordingLogger(type.getName());
    }

    /**
     * Returns the recorded log messages.
     *
     * @return the records, in logging order
     */
    public List<LogRecord> logRecords() {
        return List.copyOf(records);
    }

    /**
     * Tests whether any recorded message or exception message contains a text.
     *
     * @param text the text
     * @return {@code true} if found
     */
    public boolean logContains(String text) {
        for (LogRecord r : records) {
            if (r.message() != null && r.message().contains(text)) {
                return true;
            }
            for (Throwable t = r.thrown(); t != null; t = t.getCause()) {
                if (t.getMessage() != null && t.getMessage().contains(text)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Removes all recorded log messages. */
    public void clearLog() {
        records.clear();
    }

    private final class RecordingLogger implements System.Logger {

        private final String name;

        RecordingLogger(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isLoggable(Level level) {
            return true;
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
            records.add(new LogRecord(name, level, msg, thrown));
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String format, Object... params) {
            String msg = params == null || params.length == 0 ? format : MessageFormat.format(format, params);
            records.add(new LogRecord(name, level, msg, null));
        }

        @Override
        public void log(Level level, Supplier<String> msgSupplier) {
            records.add(new LogRecord(name, level, msgSupplier.get(), null));
        }
    }
}
