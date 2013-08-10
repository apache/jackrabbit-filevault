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
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;

/**
 * The document diff is used to create a <em>diff</em> between 2 documents.
 * It provides 2 possibilites to use the differences: either using the
 * {@link ChangeListener} which is called for each line of the document, or
 * using the {@link Hunk}s that form the differences of this diff.
 */
public class DocumentDiff {

    /**
     * the left document
     */
    private Document left;

    /**
     * the right document
     */
    private Document right;

    /**
     * the change script
     */
    private final Diff.Change change;

    /**
     * the hunks
     */
    private Hunk hunks;

    /**
     * the number of changes
     */
    private int numDelta = 0;

    /**
     * Create a new diff from the given 2 documents
     * @param left the left document
     * @param right the right document
     */
    public DocumentDiff(Document left, Document right) {
        this.left = left;
        this.right = right;
        Document.Element[] leftElems = left.getElements();
        Document.Element[] rightElems = right.getElements();
        change = new Diff(leftElems, rightElems).diff_2(false);
        init();
    }

    /**
     * Returns the number of changed elements. each insertion and each deletion
     * counts as 1 change. if elements were modified the greate change is counted.
     * eg: if you change 'foo' to 'bar' this is actually 1 deletion and 1 insertion
     * but counts as 1 change. if you change 'foo\nbar\n' to 'hello' this counts
     * as 2 changes since this includes 2 deletions.
     *
     * @return the number of changed elements.
     */
    public int getNumDeltaElements() {
        return numDelta;
    }

    /**
     * Returns the linked list of hunks
     * @return the hunks.
     */
    public Hunk getHunks() {
        return hunks;
    }

    /**
     * Same as {@link #write(Writer, int)} but to a string buffer.
     *
     * @param buf the buffer
     * @param numContextLines the number of context lines.
     */
    public void write(StringBuffer buf, int numContextLines) {
        try {
            StringWriter out = new StringWriter();
            write(new DiffWriter(out), numContextLines);
            out.close();
            buf.append(out.getBuffer());
        } catch (IOException e) {
            throw new IllegalStateException(e.toString());
        }
    }

    /**
     * Same as {@link #write(DiffWriter, int)} but to a string buffer.
     *
     * @param buf the buffer
     * @param lineSeparator the line separator to use
     * @param numContextLines the number of context lines.
     */
    public void write(StringBuffer buf, String lineSeparator, int numContextLines) {
        try {
            StringWriter out = new StringWriter();
            write(new DiffWriter(out, lineSeparator), numContextLines);
            out.close();
            buf.append(out.getBuffer());
        } catch (IOException e) {
            throw new IllegalStateException(e.toString());
        }
    }

    /**
     * Same as {@link #write(DiffWriter, int)} but wraps the given writer
     * with a default diff writer.
     *
     * @param out the writer
     * @param numContextLines the number of context lines.
     * @throws IOException if an I/O error occurs
     */
    public void write(Writer out, int numContextLines) throws IOException {
        DiffWriter dw = new DiffWriter(out);
        write(dw, numContextLines);
        dw.flush();
    }

    /**
     * Writes the differences to the given writer in a unified diff
     * format. the context lines specify how many unmodified lines should
     * sourround the actual difference.
     *
     * @param out the writer
     * @param numContextLines the number of context lines.
     * @throws IOException if an I/O error occurs
     */
    public void write(DiffWriter out, int numContextLines) throws IOException {
        if (hunks != null) {
            if (left.getSource() != null && right.getSource() != null) {
                out.write("--- ");
                out.write(left.getSource().getLocation());
                out.writeNewLine();
                out.write("+++ ");
                out.write(right.getSource().getLocation());
                out.writeNewLine();
            } else {
                out.write("--- .mine");
                out.writeNewLine();
                out.write("+++ .theirs");
                out.writeNewLine();
            }
            Hunk hunk = hunks;
            while (hunk != null) {
                hunk = hunk.write(out, numContextLines);
            }
        }
    }

    /**
     * init the hunks and the delta counter.
     */
    private void init() {
        Diff.Change c = change;
        int leftPos = 0;
        int rightPos = 0;
        Hunk first = new Hunk(null, null, 0, null);
        Hunk hunk = first;
        while (c != null) {
            numDelta += Math.max(c.deleted, c.inserted);
            if (leftPos < c.line0) {
                // add unmodified hunk
                int len = c.line0 - leftPos;
                hunk = new Hunk(
                        new Range(left, leftPos, leftPos + len),
                        new Range(right, rightPos, rightPos + len),
                        Hunk.UNMODIFIED,
                        hunk);
                leftPos+= len;
                rightPos+= len;
            }
            // add hunk
            hunk = new Hunk(
                    new Range(left, leftPos, leftPos + c.deleted),
                    new Range(right, rightPos, rightPos + c.inserted),
                    (c.deleted > 0 ? Hunk.DELETED : 0)|
                    (c.inserted > 0 ? Hunk.INSERTED : 0),
                    hunk);
            leftPos+=c.deleted;
            rightPos+=c.inserted;
            c = c.nextChange;
        }
        // last hunk
        if (leftPos < left.getElements().length) {
            int len = left.getElements().length - leftPos;
            new Hunk(
                    new Range(left, leftPos, leftPos + len),
                    new Range(right, rightPos, rightPos + len),
                    Hunk.UNMODIFIED,
                    hunk);
        }
        // and record the first valid hunk
        hunks = first.next();
    }

    /**
     * Iterate over all changes and invoke the respective methods in the given
     * listener. the context lines specify how many unmodified lines should
     * sourround the respective change.
     *
     * @param listener the change listener
     * @param numContextLines the number of context lines
     */
    public void showChanges(ChangeListener listener, int numContextLines) {
        Diff.Change c = change;
        Document.Element[] lines0 = left.getElements();
        Document.Element[] lines1 = right.getElements();
        listener.onDocumentsStart(left, right);
        while (c != null) {
            Diff.Change first = c;
            int start0 = Math.max(c.line0 - numContextLines, 0);
            int end0 = Math.min(c.line0 + c.deleted + numContextLines, lines0.length);
            int start1 = Math.max(c.line1 - numContextLines, 0);
            int end1 = Math.min(c.line1 + c.inserted + numContextLines, lines1.length);
            while (c != null) {
                if (c.line0 <= end0) {
                    end0 = Math.min(c.line0 + c.deleted + numContextLines, lines0.length);
                    end1 = Math.min(c.line1 + c.inserted + numContextLines, lines1.length);
                    c = c.nextChange;
                } else {
                    break;
                }
            }
            listener.onChangeStart(start0, end0 - start0 - 1, start1, end1 - start1 - 1);
            //dump(fmt, first, c, numContextLines);
            while (first != c) {
                while (start0 < first.line0) {
                    listener.onUnmodified(start0, start1, lines0[start0]);
                    start0++;
                    start1++;
                }
                for (int i = 0; i < first.deleted; i++) {
                    listener.onDeleted(start0, first.line1, lines0[start0]);
                    start0++;
                }
                for (int i = 0; i < first.inserted; i++) {
                    listener.onInserted(first.line0, start1, lines1[start1]);
                    start1++;
                }
                first = first.nextChange;
            }
            for (int i = 0; i < numContextLines && start0 < lines0.length; i++) {
                listener.onUnmodified(start0, start1, lines0[start0]);
                start0++;
                start1++;
            }
            listener.onChangeEnd();
        }
        listener.onDocumentsEnd(left, right);
    }

    /**
     * Returns an element factory that provides the elements of the merged
     * result of this diff where the left document is dominant.
     * @return the merged elements.
     */
    public ElementsFactory getMergedLeft() {
        return new MergeElementFactory(true);
    }

    /**
     * Returns an element factory that provides the elements of the merged
     * result of this diff where the right document is dominant.
     * @return the merged elements.
     */
    public ElementsFactory getMergedRight() {
        return new MergeElementFactory(false);
    }

    /**
     * element factory that provides the merged result.
     */
    private class MergeElementFactory implements ElementsFactory {

        /**
         * if <code>true</code>, do the merge reverse
         */
        boolean reverse;

        /**
         * Create a new element factory.
         * @param reverse if <code>true</code>, do the merge revese
         */
        public MergeElementFactory(boolean reverse) {
            this.reverse = reverse;
        }

        /**
         * {@inheritDoc}
         */
        public Document.Element[] getElements() {
            Diff.Change c = change;
            Document.Element[] lines0 = left.getElements();
            Document.Element[] lines1 = right.getElements();
            ArrayList elems = new ArrayList();
            while (c != null) {
                Diff.Change first = c;
                int start0 = 0;
                int end0 = lines0.length;
                int start1 = 0;
                int end1 = lines1.length;
                while (c != null) {
                    if (c.line0 <= end0) {
                        end0 = lines0.length;
                        end1 = lines1.length;
                        c = c.nextChange;
                    } else {
                        break;
                    }
                }
                while (first != c) {
                    while (start0 < first.line0) {
                        elems.add(lines0[start0]);
                        start0++;
                        start1++;
                    }
                    for (int i = 0; i < first.deleted; i++) {
                        if (reverse) {
                            elems.add(lines0[start0]);
                        }
                        start0++;
                    }
                    for (int i = 0; i < first.inserted; i++) {
                        if (!reverse) {
                            elems.add(lines1[start1]);
                        }
                        start1++;
                    }
                    first = first.nextChange;
                }
                while (start0 < lines0.length) {
                    elems.add(lines0[start0]);
                    start0++;
                    start1++;
                }
            }
            return (Document.Element[]) elems.toArray(new Document.Element[elems.size()]);
        }
    }

}
