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

import java.io.PrintWriter;

/**
 * Provides a default output for a diff.
 */
public class DefaultChangeListener implements ChangeListener {

    /**
     * the output writer
     */
    private final PrintWriter out;

    /**
     * debug flag
     */
    private boolean debug;

    /**
     * Creates a new default change listener that will write to the given
     * writer.
     * @param out the writer
     */
    public DefaultChangeListener(PrintWriter out) {
        this.out = out;
    }

    /**
     * Creates a new default change listener that will write to the given
     * writer. if debug is <code>true</code> the line numbers are also included
     * in the output.
     *
     * @param out the writer
     * @param debug flag
     */
    public DefaultChangeListener(PrintWriter out, boolean debug) {
        this.out = out;
        this.debug = debug;
    }

    /**
     * {@inheritDoc}
     */
    public void onDocumentsStart(Document left, Document right) {
        out.println("Start Diff");
    }

    /**
     * {@inheritDoc}
     */
    public void onDocumentsEnd(Document left, Document right) {
        out.println("End Diff");
    }

    /**
     * {@inheritDoc}
     */
    public void onChangeStart(int leftLine, int leftLen, int rightLine, int rightLen) {
        out.println("@@ -" + (leftLine+1) + "," + leftLen + " +" + (rightLine+1) + "," + rightLen + " @@");
    }

    /**
     * {@inheritDoc}
     */
    public void onChangeEnd() {
        out.flush();
    }

    /**
     * {@inheritDoc}
     */
    public void onUnmodified(int leftLine, int rightLine, Document.Element text) {
        if (debug) {
            out.print("(" + (leftLine+1) + "," + (rightLine+1) + ") ");
        }
        out.println(text);
    }

    /**
     * {@inheritDoc}
     */
    public void onDeleted(int leftLine, int rightLine, Document.Element text) {
        if (debug) {
            out.print("(" + (leftLine+1) + "," + (rightLine+1) + ") ");
        }
        out.print("-");
        out.println(text);
    }

    /**
     * {@inheritDoc}
     */
    public void onInserted(int leftLine, int rightLine, Document.Element text) {
        if (debug) {
            out.print("(" + (leftLine+1) + "," + (rightLine+1) + ") ");
        }
        out.print("+");
        out.println(text);
    }

}
