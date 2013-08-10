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

/**
 * Implements a tree-way diff between a base document and 2 dervied ones.
 * The result of the diff operation is a list of {@link Hunk3} that can
 * record the changes and conflicts.
 */
public class DocumentDiff3 {

    /**
     * the base document
     */
    private final Document base;

    /**
     * the left document
     */
    private final Document left;

    /**
     * the right documents
     */
    private final Document right;

    /**
     * the script of changes from the base to the left document
     */
    private final Diff.Change leftChanges;

    /**
     * the script of changes from the base to the right document
     */
    private final Diff.Change rightChanges;

    /**
     * the chain of {@link Hunk3}s of this diff. this hunk here is just a
     * sentinel so that the chain is never empty.
     */
    private final Hunk3 hunks = new Hunk3(null, null, null, null);

    /**
     * flag that indicates that this diff has conflicts
     */
    private boolean hasConflicts;

    /**
     * Creates a new document diff 3 object.
     * @param base the base document
     * @param left the left document
     * @param right the right document
     */
    public DocumentDiff3(Document base, Document left, Document right) {
        this.base = base;
        this.left = left;
        this.right = right;
        Document.Element[] baseElems = base.getElements();
        Document.Element[] leftElems = left.getElements();
        Document.Element[] rightElems = right.getElements();
        leftChanges = new Diff(baseElems, leftElems).diff_2(false);
        rightChanges = new Diff(baseElems, rightElems).diff_2(false);
        initHunks();
    }

    /**
     * Returns a chain of {@link Hunk3}s that contain the modifications.
     * @return a chain of {@link Hunk3}s.
     */
    public Hunk3 getHunks() {
        return hunks.next();
    }

    /**
     * Indicates if any of the hunks has a conflict.
     * @return <code>true</code> if any of the hunks has a conflict.
     */
    public boolean hasConflicts() {
        return hasConflicts;
    }

    /**
     * Writes the resulting document to the given string buffer. this may include
     * conflicting regions.
     *
     * @param buf the string buffer to write to
     * @param lineSeparator the line separator to use
     * @param showBase if set to <code>true</code> the base section of a conflict
     *        is also included in the output.
     */
    public void write(StringBuffer buf, String lineSeparator, boolean showBase) {
        try {
            StringWriter w = new StringWriter();
            write(new DiffWriter(w, lineSeparator), showBase);
            buf.append(w.getBuffer());
        } catch (IOException e) {
            throw new IllegalStateException(e.toString());
        }
    }
    
    /**
     * Writes the resulting document to the given write. this may include
     * conflicting regions.
     *
     * @param w the writer to write to
     * @param showBase if set to <code>true</code> the base section of a conflict
     *        is also included in the output.
     * @throws IOException if an I/O error occurs
     */
    public void write(DiffWriter w, boolean showBase) throws IOException {
        for (Hunk3 hunk = hunks.next(); hunk != null; hunk = hunk.next()) {
            hunk.write(w, showBase);
        }
        w.flush();
    }

    /**
     * initializes the hunks
     */
    private void initHunks() {
        MyChange[] changes = {
                wrap(leftChanges),
                wrap(rightChanges)
        };
        int basePos = 0;
        Hunk3 hunk = hunks; // the last hunk
        while (changes[0] != null || changes[1] != null) {
            MyChange[] using = {null, null};
            MyChange[] lastUsing = {null, null};

            int baseIdx;
            if (changes[0] == null) {
                baseIdx = 1;
            } else if (changes[1] == null) {
                baseIdx = 0;
            } else {
                // use the change that is smaller
                baseIdx = changes[0].low0 < changes[1].low0 ? 0 : 1;
            }
            int highIdx = baseIdx;
            int highMark = changes[highIdx].high0;

            // add the change to the using set and unchain it
            using[highIdx] = lastUsing[highIdx] = changes[highIdx];
            changes[highIdx] = changes[highIdx].next;
            lastUsing[highIdx].next = null;

            int otherIdx = highIdx^1;
            MyChange other = changes[otherIdx];

            // search for region that ends in a 'void' of changes
            // i.e. when the end of the 'other' is greater than the high mark.
            //
            // a    a     a
            // a    a b   a b
            //   b    b   a
            //   b
            while (other != null && other.low0 <= highMark) {
                // add this change to the using set
                if (using[otherIdx] == null) {
                    using[otherIdx] = other;
                } else {
                    using[otherIdx].next = other;
                }
                lastUsing[otherIdx] = other;

                // advance other and unchain it
                changes[otherIdx] = changes[otherIdx].next;
                other.next = null;
                
                // if the high mark is beyond the end of the other diff
                // we're finished
                if (other.high0 > highMark) {
                    // switch roles
                    highIdx ^= 1;
                    highMark = other.high0;
                }
                otherIdx = highIdx^1;
                other = changes[otherIdx];
            }

            // now build the hunks from the set of changes in 'using'
            // first deal with the stuff that was common before the first change
            int lowMark = using[baseIdx].low0;
            if (basePos < lowMark) {
                hunk = new Hunk3(
                        new Range(base, basePos, lowMark),
                        null,
                        null,
                        hunk);
                basePos = lowMark;
                //System.out.println(hunks.getLast().toString());
            }

            // get the ranges for the changsets
            int[] deltaLow = {0,0};
            if (using[baseIdx^1] != null) {
                deltaLow[baseIdx^1] = using[baseIdx^1].low0 - using[baseIdx].low0;
            }
            int[] deltaHigh = {0,0};
            if (using[highIdx^1] != null) {
                deltaHigh[highIdx^1] = using[highIdx].high0 - using[highIdx^1].high0;
            }
            Range leftRange = null;
            Range rightRange = null;
            if (using[0] != null) {
                leftRange = new Range(left, using[0].low1 - deltaLow[0], lastUsing[0].high1 + deltaHigh[0]);
            }
            if (using[1] != null) {
                rightRange = new Range(right, using[1].low1 - deltaLow[1], lastUsing[1].high1 + deltaHigh[1]);
            }
            // check if the conflict is really one
            boolean conflict = false;
            if (leftRange != null && rightRange != null) {
                if (leftRange.len() == rightRange.len()) {
                    for (int i=0; i< leftRange.len(); i++) {
                        if (!left.getElements()[leftRange.low + i].equals(right.getElements()[rightRange.low + i])) {
                            // yes, it is
                            conflict = true;
                            break;
                        }
                    }
                    // if all lines match, we can discard one of the ranges
                    if (!conflict) {
                        rightRange = null;
                    }
                } else {
                    conflict = true;
                }
            }
            // get the range for the base
            int baseHigh = using[highIdx].high0;
            Range baseRange = new Range(base, basePos, baseHigh);
            basePos = baseHigh;
            // and create new hunk
            hunk = new Hunk3(baseRange, leftRange, rightRange, hunk);
            hasConflicts |= conflict;
            //System.out.println(hunks.getLast().toString());
        } /* while */

        // deal with last hunk
        if (basePos < base.getElements().length) {
            new Hunk3(
                    new Range(base, basePos, base.getElements().length),
                    null,
                    null,
                    hunk);
            //System.out.println(hunks.getLast().toString());
        }
    }

    /**
     * Wraps a chain of {@link Diff.Change}s with a list of {@link MyChange}s.
     * this is rather a convencience wrapping so that the algorithm above is
     * easier to understand.
     *
     * @param df the chain of changes
     * @return the wrapped chain of changes
     */
    private MyChange wrap(Diff.Change df) {
        // init sentinel
        MyChange first = null;
        MyChange c = null;
        while (df != null) {
            c = new MyChange(df.line0, df.line1, df.deleted, df.inserted, c);
            if (first == null) {
                first = c;
            }
            df = df.nextChange;
        }
        return first;
    }

    /**
     * Dumps a chain of my changes.
     * @param c the change
     * @param left the left doc
     * @param right the right doc
     */
    private void dump(MyChange c, Document left, Document right) {
        while (c != null) {
            if (c.isInsert()) {
                for (int i=0; i<c.high1-c.low1; i++) {
                    dump(0, c.low0 + i, c.low1 + i, "+", c.low1 + i, right);
                }
            } else if (c.isDelete()) {
                for (int i=0; i<c.high0-c.low0; i++) {
                    dump(0, c.low0 + i, c.low1 + i, "-", c.low0 + i, left);
                }
            } else {
                for (int i=0; i<c.high1-c.low1; i++) {
                    dump(0, c.low0 + i, c.low1 + i, "~", c.low1 + i, right);
                }
            }
            c = c.next;
        }
    }

    /**
     * prints a element line
     * @param b the base line number
     * @param l the left line number
     * @param r the right line number
     * @param prefix the prefix
     * @param i the index
     * @param doc the document
     */
    private void dump(int b, int l, int r, String prefix, int i, Document doc) {
        StringBuffer buf = new StringBuffer();
        buf.append("(").append(b);
        buf.append(", ").append(l);
        buf.append(", ").append(r);
        buf.append(") ").append(prefix);
        buf.append(doc.getElements()[i]);
        System.out.println(buf);
    }

    /**
     * Wrapper class for the {@link Diff.Change}. This is mainly used to
     * make the merge algorithm easier, but might result in a performace
     * drop if the change list is huge.
     */
    private static class MyChange {

        /**
         * the low line in the left document
         */
        private final int low0;

        /**
         * the low line in the right document
         */
        private final int low1;

        /**
         * the high line in the left document
         */
        private final int high0;

        /**
         * the high line in the right document
         */
        private final int high1;

        /**
         * the next change
         */
        private MyChange next;

        /**
         * Constructs a new change and adds it the the previous one.
         * @param low0 the low line of the left document
         * @param low1 the low line of the right document
         * @param len0 the length of the change in the left document
         * @param len1 the length of the change in the right document
         * @param prev the previous change
         */
        public MyChange(int low0, int low1, int len0, int len1, MyChange prev) {
            this.low0 = low0;
            this.low1 = low1;
            this.high0 = low0 + len0;
            this.high1 = low1 + len1;
            if (prev != null) {
                prev.next = this;
            }
        }

        /**
         * Checks if this is a deletion.
         * @return <code>true</code> if this is a deletion.
         */
        public boolean isDelete() {
            return low1 == high1;
        }

        /**
         * Checks if this is an insertion
         * @return <code>false</code> if this is an insertion.
         */
        public boolean isInsert() {
            return low0 == high0;
        }

        /**
         * Returns a debug string for this change.
         * @return a debug string for this change.
         */
        public String toString() {
            return "(" + low0 + "-" + high0 + "),(" + low1 + "-" + high1 + ")";
        }
    }

}