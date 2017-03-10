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

/**
 * A document represents a list of elements and have a source.
 */
public class Document {

    /**
     * the source information of this document
     */
    private final DocumentSource source;

    /**
     * the array of elements that form this document
     */
    private final Element[] elements;

    /**
     * Create a new document with the given source and element factory
     * @param source the source
     * @param factory the element factory
     */
    public Document(DocumentSource source, ElementsFactory factory) {
        this.source = source;
        this.elements = factory.getElements();
    }

    /**
     * Return the source of this document
     * @return the source.
     */
    public DocumentSource getSource() {
        return source;
    }

    /**
     * Return the elements of this document
     * @return the elements.
     */
    public Element[] getElements() {
        return elements;
    }

    /**
     * Create a <em>diff</em> between this document and the given one.
     *
     * @param right the other document to diff to.
     * @return a diff.
     */
    public DocumentDiff diff(Document right) {
        return new DocumentDiff(this, right);
    }

    /**
     * Create a <em>diff</em> between the given document and this one.
     * @param left the other document.
     * @return a diff
     */
    public DocumentDiff reverseDiff(Document left) {
        return new DocumentDiff(left, this);
    }

    /**
     * Create a <em>tree-way-diff</em> using this document as base.
     *
     * @param left the left document
     * @param right the right document
     * @return a diff3
     */
    public DocumentDiff3 diff3(Document left, Document right) {
        return new DocumentDiff3(this, left, right);
    }

    /**
     * Elements form a document.
     */
    public static interface Element {

        /**
         * Returns the string representation of this element. If the elements
         * were generated originally from a string they should return the
         * exact string again.
         * @return the string of this element.
         */
        String getString();
    }

    /**
     * The annotated element include the document source. This can be used
     * to create an annotated document. 
     */
    public static interface AnnotatedElement extends Element {

        /**
         * Returns the document source of this element.
         * @return the source of this element.
         */
        DocumentSource getDocumentSource();

    }
}
