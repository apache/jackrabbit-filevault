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

/**
 * A hunk records a block of diff between the left and the right document.
 * it represents either a insertion, deletion, change or identiy. several
 * hunks are chained in a linked list.
 */
public class Hunk {

    /**
     * type indicates an unmodified block
     */
    public static final int UNMODIFIED = 0;

    /**
     * type indicates an inserted block
     */
    public static final int INSERTED = 1;

    /**
     * type indicates a deleted block
     */
    public static final int DELETED = 2;

    /**
     * type indicates a changed block
     */
    public static final int CHANGED = INSERTED | DELETED;

    /**
     * the range in the left document
     */
    public final Range left;

    /**
     * the rnage in the right document
     */
    public final Range right;

    /**
     * the hunk type
     */
    public final int type;

    /**
     * the previous hunk
     */
    private Hunk prev;

    /**
     * the next hunk
     */
    private Hunk next;

    /**
     * Creates a new hunk and adds it to the previous hunk
     * @param left the left range
     * @param right the right range
     * @param type the hunk type
     * @param prev the previous hunk
     */
    public Hunk(Range left, Range right, int type, Hunk prev) {
        this.left = left;
        this.right = right;
        this.prev = prev;
        this.type = type;
        if (prev != null) {
            prev.next = this;
        }
    }

    /**
     * Returns the previous hunk of this chain
     * @return the previous hunk.
     */
    public Hunk prev() {
        return prev;
    }

    /**
     * Returns the next hunk of this chain
     * @return the next hunk.
     */
    public Hunk next() {
        return next;
    }

    /**
     * Writes a unified diff to the given writer and returns the next hunk to
     * continue the output.
     *
     * @param out the writer
     * @param numContextLines the number of context lines to include in the
     *        diff blocks. do not change during iteration!
     * @return the next hunk or <code>null</code>.
     * @throws IOException if an I/O error occurs.
     */
    public Hunk write(DiffWriter out, int numContextLines) throws IOException {
        if (type == UNMODIFIED) {
            //writeData(out, left, " ");
            return next;
        } else {
            Hunk last = next;
            Hunk prevLast = this;

            // search hunk that is big enough to hold 2 x numContextLines or is
            // the last hunk.
            while (last != null) {
                if (last.type == UNMODIFIED
                        &&
                        (last.left.len() > 2*numContextLines || last.next == null)) {
                    break;
                }
                prevLast = last;
                last = last.next;
            }

            // get new ranges for diff line
            Range prevRange;
            if (prev == null) {
                // this is the very first hunk, so no previous context lines
                prevRange = new Range(left.doc, left.low, left.low);
            } else {
                // the search above ensures that the previous one never overlaps
                // this hunk. we just need to guard the very first hunk
                prevRange = new Range(left.doc, Math.max(left.low - numContextLines, 0), left.low);
            }

            Range lastRange;
            if (last == null) {
                // if we did not find a last hunk, create an empty range
                // with the bounds of the hunk previous to the last
                lastRange = new Range(left.doc, prevLast.left.high, prevLast.left.high);
            } else {
                // we are not allowed to enlarge the range
                lastRange = new Range(left.doc, last.left.low, Math.min(last.left.high, last.left.low + numContextLines));
            }

            // handle special case where our diff differs from the GNU diff
            // i reckon this is a bug in {@link Diff} where the line number
            // of a deletion or insertion is 1 too big.
            int prevLen0 = prevRange.len();
            int lastLen0 = lastRange.len();
            int prevLen1 = prevRange.len();
            int lastLen1 = lastRange.len();
            if (left.len() == 0 && numContextLines == 0) {
                prevLen0 = 1;
                lastLen0--;
            }
            if (right.len() == 0 && numContextLines == 0) {
                prevLen1 = 1;
                lastLen1--;
            }
            Range newLeft = new Range(left.doc,
                    left.low - prevLen0,
                    prevLast.left.high + lastLen0);
            Range newRight = new Range(right.doc,
                    right.low - prevLen1,
                    prevLast.right.high + lastLen1);
            out.write("@@ -");
            out.write(newLeft.toRangeString());
            out.write(" +");
            out.write(newRight.toRangeString());
            out.write(" @@");
            out.writeNewLine();

            // output previous context line
            writeData(out, prevRange, " ");
            Hunk h = this;
            while (h != last) {
                h.writeBody(out);
                h = h.next;
            }
            writeData(out, lastRange, " ");
            return last;
        }
    }

    /**
     * Writes the body of this hunk
     * @param out the writer
     * @throws IOException if an I/O error occurs.
     */
    private void writeBody(DiffWriter out) throws IOException {
        if (type == UNMODIFIED) {
            writeData(out, left, " ");
        }
        if ((type & DELETED) != 0) {
            writeData(out, left, "-");
        }
        if ((type & INSERTED) != 0) {
            writeData(out, right, "+");
        }
    }

    /**
     * Write the actual lines
     * @param out the writer
     * @param range the range
     * @param prefix the prefix
     * @throws IOException if an I/O error occurs
     */
    private static void writeData(DiffWriter out, Range range, String prefix)
            throws IOException {
        for (int i=range.low; i<range.high; i++) {
            out.write(prefix);
            out.write(range.doc.getElements()[i].getString());
        }
    }
}