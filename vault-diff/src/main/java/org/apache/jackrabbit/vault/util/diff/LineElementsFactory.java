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

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;

/**
 * Implements an element factory that creates elements from the lines of an
 * input text.
 */
public class LineElementsFactory implements ElementsFactory {

    /**
     * the maximum numbers of lines
     */
    private static final int MAX_ELEMENTS = 100000;

    /**
     * the elements
     */
    private final Document.Element[] elements;

    /**
     * private constructor
     * @param elements the elements
     */
    private LineElementsFactory(Document.Element[] elements) {
        this.elements = elements;
    }

    /**
     * {@inheritDoc}
     */
    public Document.Element[] getElements() {
        return elements;
    }

    /**
     * Create a new line element factory for the given text.
     * @param source the document source
     * @param text the text
     * @param ignoreWs if <code>true</code> white spaces are ignored for the diff
     * @return the new factory
     *
     * todo: create non-annotated variant
     */
    public static LineElementsFactory create(DocumentSource source, String text, boolean ignoreWs) {
        try {
            Reader reader = new StringReader(text);
            return create(source, reader, ignoreWs);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.toString());
        }
    }

    /**
     * Create a new line element factory for the given source.
     * @param source the file source
     * @param ignoreWs if <code>true</code> white spaces are ignored for the diff
     * @param charset the charset
     * @return the new factory
     * @throws IOException if an I/O error occurs
     *
     * todo: create non-annotated variant
     */
    public static LineElementsFactory create(FileDocumentSource source, boolean ignoreWs, String charset)
            throws IOException {
        Reader text = charset == null
                ? new FileReader(source.getFile())
                : new InputStreamReader(new FileInputStream(source.getFile()), charset);
        try {
            return create(source, text, ignoreWs);
        } finally {
            try {
                text.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * Create a new line element factory for the given source.
     * @param source the source
     * @param text the text
     * @param ignoreWs if <code>true</code> white spaces are ignored for the diff
     * @return the new factory
     * @throws IOException if an I/O error occurs
     *
     * todo: create non-annotated variant
     */
    public static LineElementsFactory create(DocumentSource source, Reader text, boolean ignoreWs)
            throws IOException {
        Document.Element[] elements = getElements(source, text, ignoreWs);
        return new LineElementsFactory(elements);
    }

    /**
     * Read the input and split into elements
     * @param source the source
     * @param r the text
     * @param ignoreWS ignore flag
     * @return the elements array
     * @throws IOException if an I/O error occurs
     */
    private static Document.Element[] getElements(DocumentSource source, Reader r, boolean ignoreWS)
            throws IOException {
        if (r == null) {
            return new Document.Element[0];
        }
        ArrayList lines = new ArrayList();
        char[] buffer = new char[8192];
        int start = 0;
        int pos = 0;
        int end = r.read(buffer);
        while (pos < end) {
            char c = buffer[pos++];
            if (c == '\n') {
                String line = new String(buffer, start, pos - start);
                if (ignoreWS) {
                    lines.add(new LineElementsFactory.IStringElement(source, line));
                } else {
                    lines.add(new LineElementsFactory.StringElement(source, line));
                }
                start = pos;
                if (lines.size() == MAX_ELEMENTS) {
                    break;
                }
            }
            if (pos == end) {
                // shift buffer and read more
                int len = end - start;
                if (len == buffer.length) {
                    // line very long - double buffer size
                    char[] newBuffer = new char[buffer.length * 2];
                    System.arraycopy(buffer, start, newBuffer, 0, len);
                    buffer = newBuffer;
                } else if (len > 0) {
                    System.arraycopy(buffer, start, buffer, 0, len);
                }
                end = len;
                start = 0;
                pos = 0;
                int read = r.read(buffer, end, buffer.length - end);
                if (read < 0) {
                    break;
                }
                end += read;
            }
        }

        // add last line if not terminated by a line feed
        if (start < end) {
            String line = new String(buffer, start, end - start);
            if (ignoreWS) {
                lines.add(new LineElementsFactory.IStringElement(source, line));
            } else {
                lines.add(new LineElementsFactory.StringElement(source, line));
            }
        }
        if (ignoreWS) {
            return (LineElementsFactory.IStringElement[]) lines.toArray(new LineElementsFactory.IStringElement[lines.size()]);
        } else {
            return (LineElementsFactory.StringElement[]) lines.toArray(new LineElementsFactory.StringElement[lines.size()]);
        }
    }

    /**
     * An element that is based on a string
     * todo: create non-annotated varian
     */
    public static class StringElement implements Document.AnnotatedElement {

        private final DocumentSource source;

        private final String string;

        public StringElement(DocumentSource source, String string) {
            this.source = source;
            this.string = string;
        }

        public String getString() {
            return string;
        }

        public DocumentSource getDocumentSource() {
            return source;
        }

        public int hashCode() {
            return string.hashCode();
        }


        public String toString() {
            return string;
        }

        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof LineElementsFactory.StringElement) {
                return ((LineElementsFactory.StringElement) obj).string.equals(string);
            }
            return false;
        }
    }

    /**
     * An element that is based on a string but ignores the whitespaces in the
     * equals method.
     * todo: create non-annotated varian
     */
    public static class IStringElement implements Document.AnnotatedElement {

        private final DocumentSource source;

        private final String string;

        private String stripped;


        public DocumentSource getDocumentSource() {
            return source;
        }

        public String getString() {
            return string;
        }

        public IStringElement(DocumentSource source, String string) {
            this.source = source;
            this.string = string;
        }

        private String getStripped() {
            if (stripped == null) {
                StringBuffer buf = new StringBuffer(string.length());
                for (int i = 0; i < string.length(); i++) {
                    char c = string.charAt(i);
                    if (!Character.isWhitespace(c)) {
                        buf.append(c);
                    }
                }
                stripped = buf.toString();
            }
            return stripped;
        }

        public int hashCode() {
            return getStripped().hashCode();
        }

        public boolean equals(Object obj) {
            assert obj instanceof LineElementsFactory.IStringElement;
            return getStripped().equals(((LineElementsFactory.IStringElement) obj).getStripped());
        }

        public String toString() {
            return string;
        }
    }

}
