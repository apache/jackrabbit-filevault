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
 * Listener that is called for each line in a document.
 * See {@link DocumentDiff#showChanges(ChangeListener, int)} for details.
 */
public interface ChangeListener {

    /**
     * Invoked before the iteration over the changes start.
     * @param left the left document
     * @param right the right document
     */
    void onDocumentsStart(Document left, Document right);

    /**
     * Invoked after the iteration over the changes finished.
     * @param left the left document
     * @param right the right document
     */
    void onDocumentsEnd(Document left, Document right);

    /**
     * Invoked before a change starts.
     * @param leftElem the index of the left element of this change.
     * @param leftLen the number of changed left elements.
     * @param rightElem the index of the right element of this change.
     * @param rightLen the number of changed right elements.
     */
    void onChangeStart(int leftElem, int leftLen, int rightElem, int rightLen);

    /**
     * Invoked after a change finished.
     */
    void onChangeEnd();

    /**
     * Invoked for an unmodified element
     * @param leftIdx the index of the left element
     * @param rightIdx the index of the right element
     * @param elem the element
     */
    void onUnmodified(int leftIdx, int rightIdx, Document.Element elem);

    /**
     * Invoked for a deleted element
     * @param leftIdx the index of the left element
     * @param rightIdx the index of the right element
     * @param elem the element
     */
    void onDeleted(int leftIdx, int rightIdx, Document.Element elem);

    /**
     * Invoked for an inserted element
     * @param leftIdx the index of the left element
     * @param rightIdx the index of the right element
     * @param elem the element
     */
    void onInserted(int leftIdx, int rightIdx, Document.Element elem);

}
