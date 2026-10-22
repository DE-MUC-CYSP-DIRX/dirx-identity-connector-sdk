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

package solutions.dirx.identity.connector.sdk.samples.csv;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal reader and writer for comma-separated values as described by RFC 4180: fields may be
 * enclosed in double quotes, quotes inside quoted fields are doubled, and quoted fields may
 * contain separators and line breaks.
 */
public final class CsvFormat {

    private final char separator;

    /**
     * Creates a format.
     *
     * @param separator the field separator, e.g. {@code ','} or {@code ';'}
     */
    public CsvFormat(char separator) {
        if (separator == '"' || separator == '\r' || separator == '\n') {
            throw new IllegalArgumentException("invalid separator");
        }
        this.separator = separator;
    }

    /**
     * Reads all records. The reader must support {@link Reader#mark(int)}.
     *
     * @param in the input
     * @return the records; empty lines are skipped
     * @throws IOException if reading fails or a quoted field is not terminated
     */
    public List<List<String>> read(Reader in) throws IOException {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldStarted = false;
        int c;
        while ((c = in.read()) != -1) {
            char ch = (char) c;
            if (quoted) {
                if (ch != '"') {
                    field.append(ch);
                    continue;
                }
                in.mark(1);
                int next = in.read();
                if (next == '"') {
                    field.append('"');
                } else {
                    quoted = false;
                    if (next != -1) {
                        in.reset();
                    }
                }
            } else if (ch == '"' && field.isEmpty()) {
                quoted = true;
                fieldStarted = true;
            } else if (ch == separator) {
                record.add(field.toString());
                field.setLength(0);
                fieldStarted = true;
            } else if (ch == '\n' || ch == '\r') {
                if (fieldStarted) {
                    record.add(field.toString());
                    records.add(record);
                }
                record = new ArrayList<>();
                field.setLength(0);
                fieldStarted = false;
            } else {
                field.append(ch);
                fieldStarted = true;
            }
        }
        if (quoted) {
            throw new IOException("unterminated quoted field");
        }
        if (fieldStarted) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }

    /**
     * Writes one record followed by CRLF. Fields are quoted only if necessary.
     *
     * @param out    the output
     * @param fields the fields
     * @throws IOException if writing fails
     */
    public void write(Writer out, List<String> fields) throws IOException {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                out.write(separator);
            }
            String f = fields.get(i) == null ? "" : fields.get(i);
            if (f.isEmpty() && fields.size() == 1
                    || f.indexOf(separator) >= 0 || f.indexOf('"') >= 0 || f.indexOf('\n') >= 0 || f.indexOf('\r') >= 0) {
                out.write('"');
                out.write(f.replace("\"", "\"\""));
                out.write('"');
            } else {
                out.write(f);
            }
        }
        out.write("\r\n");
    }
}
