/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.vault.util.diff;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

/**
 * Implements a writer that provides an additional method {@link #writeNewLine()}
 * that can be used for writing line separators which can be defined. A
 * {@link PrintWriter} would actually be better, but it does not support
 * defining the line separator to use.
 */
public class DiffWriter extends Writer {

    /**
     * native line separator
     */
    public static final String LS_NATIVE = System.getProperty("line.separator");

    /**
     * unix line separator
     */
    public static final String LS_UNIX = "\n";

    /**
     * windows line separator
     */
    public static final String LS_WINDOWS = "\r\n";

    /**
     * the wrapped writer
      */
    private final Writer out;

    /**
     * the line seperator to use for {@link #writeNewLine()}
     */
    private String lineSeparator = LS_NATIVE;

    public DiffWriter(Writer out) {
        this.out = out;
    }

    /**
     * @param lineSeparator the line seperator to use for {@link #writeNewLine()}
     */
    public DiffWriter(Writer out, String lineSeparator) {
        this.out = out;
        this.lineSeparator = lineSeparator;
    }

    /**
     * Writes a new line according to the defined line separator
     * @throws IOException if an I/O error occurs
     */
    public void writeNewLine() throws IOException {
        write(lineSeparator);
    }

    /**
     * {@inheritDoc}
     */
    public void write(int c) throws IOException {
        out.write(c);
    }

    /**
     * {@inheritDoc}
     */
    public void write(char[] cbuf) throws IOException {
        out.write(cbuf);
    }

    /**
     * {@inheritDoc}
     */
    public void write(char[] cbuf, int off, int len) throws IOException {
        out.write(cbuf, off, len);
    }

    /**
     * {@inheritDoc}
     */
    public void write(String str) throws IOException {
        out.write(str);
    }

    /**
     * {@inheritDoc}
     */
    public void write(String str, int off, int len) throws IOException {
        out.write(str, off, len);
    }

    /**
     * {@inheritDoc}
     */
    public void flush() throws IOException {
        out.flush();
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        out.close();
    }
}