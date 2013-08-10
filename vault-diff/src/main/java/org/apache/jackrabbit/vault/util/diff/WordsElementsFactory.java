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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;

/**
 * Implements an element factory that creates the elements out from words of
 * the given input stream.
 */
public class WordsElementsFactory implements ElementsFactory {

    private static final int MAX_ELEMENTS = 100000;

    private final Document.Element[] elements;

    public WordsElementsFactory(Document.Element[] elements) {
        this.elements = elements;
    }

    public Document.Element[] getElements() {
        return elements;
    }

    public static WordsElementsFactory create(DocumentSource source, String text) {
        try {
            Reader reader = new StringReader(text);
            return create(source, reader);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.toString());
        }
    }

    public static WordsElementsFactory create(DocumentSource source, Reader text)
            throws IOException {
        Document.Element[] elements = getElements(source, text);
        return new WordsElementsFactory(elements);
    }

    private static Document.Element[] getElements(DocumentSource source, Reader text)
            throws IOException {
        BufferedReader r;
        if (text instanceof BufferedReader) {
            r = (BufferedReader) text;
        } else {
            r = new BufferedReader(text);
        }
        ArrayList lines = new ArrayList();
        StringBuffer gutter = new StringBuffer();
        StringBuffer word = new StringBuffer();
        int c;
        while ((c=r.read()) >=0 && lines.size()<MAX_ELEMENTS) {
            if (Character.isLetterOrDigit((char) c)) {
                if (gutter.length() > 0) {
                    lines.add(new WordElement(source, word.toString(), gutter.toString()));
                    gutter.setLength(0);
                    word.setLength(0);
                }
                word.append((char) c);
            } else {
                gutter.append((char) c);
            }
        }
        if (word.length() > 0) {
            lines.add(new WordElement(source, word.toString(), gutter.toString()));
        }
        return (WordElement[]) lines.toArray(new WordElement[lines.size()]);
    }

    public static class WordElement implements Document.Element {

        private final DocumentSource source;

        private final String word;

        private final String gutter;

        public WordElement(DocumentSource source, String word, String gutter) {
            this.source = source;
            this.word = word;
            this.gutter = gutter;
        }

        public String getString() {
            return word + gutter;
        }

        public DocumentSource getDocumentSource() {
            return source;
        }

        public int hashCode() {
            return word.hashCode();
        }

        public String toString() {
            return getString();
        }

        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof WordElement) {
                return ((WordElement) obj).word.equals(word);
            }
            return false;
        }
    }
}
