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
 */
public class InlineChangeListener implements ChangeListener {

    private final PrintWriter out;

    public InlineChangeListener(PrintWriter out) {
        this.out = out;
    }

    public void onDocumentsStart(Document left, Document right) {
        // ignore
    }

    public void onDocumentsEnd(Document left, Document right) {
        out.flush();
    }

    public void onChangeStart(int leftLine, int leftLen, int rightLine, int rightLen) {
        // ignore
    }

    public void onChangeEnd() {
        out.flush();
    }

    public void onUnmodified(int leftLine, int rightLine, Document.Element text) {
        out.print(text);
    }

    public void onDeleted(int leftLine, int rightLine, Document.Element text) {
        String author = null;
        if (text instanceof Document.AnnotatedElement) {
            Document.AnnotatedElement t = (Document.AnnotatedElement) text;
            if (t.getDocumentSource() instanceof DefaultDocumentSource) {
                DefaultDocumentSource src = (DefaultDocumentSource) t.getDocumentSource();
                author = src.getAuthor();
            }
        }
        out.print("<del");
        if (author != null) {
            out.print(" title=\"");
            out.print(author);
            out.print("\"");
        }
        out.print(">");
        out.print(text);
        out.print("</del>");
    }

    public void onInserted(int leftLine, int rightLine, Document.Element text) {
        String author = null;
        if (text instanceof Document.AnnotatedElement) {
            Document.AnnotatedElement t = (Document.AnnotatedElement) text;
            if (t.getDocumentSource() instanceof DefaultDocumentSource) {
                DefaultDocumentSource src = (DefaultDocumentSource) t.getDocumentSource();
                author = src.getAuthor();
            }
        }
        out.print("<ins");
        if (author != null) {
            out.print(" title=\"");
            out.print(author);
            out.print("\"");
        }
        out.print(">");
        out.print(text);
        out.print("</ins>");
    }
}
