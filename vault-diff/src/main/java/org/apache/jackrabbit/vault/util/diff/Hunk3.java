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
 * A hunk3 represents a block of a change from the 3-way diff. it can either be
 * a modification on the left side (for which {@link #getLeftRange()} is not
 * <code>null</code>), or a change on the right side (for which
 * {@link #getRightRange()} is not <code>null</code>. if the left and the right
 * ranges are <code>null</code> this hunk represents an unmodified block of the
 * base document. if both ranges are NOT <code>null</code> it represents a
 * conflicting block.
 */
public class Hunk3 {

    /**
     * the left document marker pattern
     */
    public static final String[] MARKER_L  = new String[]{"<<<<<<< ", ".mine"};

    /**
     * the right document marker pattern
     */
    public static final String[] MARKER_R  = new String[]{">>>>>>> ", ".theirs"};

    /**
     * the base document marker pattern
     */
    public static final String[] MARKER_B  = new String[]{"||||||| ", ".base"};

    /**
     * the separation marker pattern
     */
    public static final String[] MARKER_M  = new String[]{"=======", ""};

    /**
     * the base range. never <code>null</code>.
     */
    private final Range base;

    /**
     * the left range
     */
    private final Range left;

    /**
     * the right range
     */
    private final Range right;

    /**
     * the next hunk in this chain
     */
    private Hunk3 next;

    /**
     * Constructs a new hunk and appends it to the previous one.
     * @param base the base document
     * @param left the left document
     * @param right the right document
     * @param prev the previous hunk
     */
    public Hunk3(Range base, Range left, Range right, Hunk3 prev) {
        this.base = base;
        this.left = left;
        this.right = right;
        if (prev != null) {
            prev.next = this;
        }
    }

    /**
     * Returns the next hunk in this chain or <code>null</code> of this hunk is
     * the last one.
     * @return the next hunk.
     */
    public Hunk3 next() {
        return next;
    }

    /**
     * Returns the range of the base document this hunk spans.
     * @return the base range.
     */
    public Range getBaseRange() {
        return base;
    }

    /**
     * Returns the range of the left document this hunk spans.
     * can be <code>null</code>.
     * @return the left range.
     */
    public Range getLeftRange() {
        return left;
    }

    /**
     * Returns the range of the right document this hunk spans.
     * can be <code>null</code>.
     * @return the right range.
     */
    public Range getRightRange() {
        return right;
    }

    /**
     * Writes this hunk to a writer. if this hunk represents a conflict it is
     * included in a merge-like manner using the separators defined above.
     *
     * @param out the writer
     * @param showBase include the block of the base document in case of a
     *        conflict as well.
     * @throws IOException if a I/O error occurs.
     */
    public void write(DiffWriter out, boolean showBase) throws IOException {
        boolean conflict = left != null && right != null;
        if (conflict) {
            out.write(getMarker(MARKER_L, left.doc));
            out.writeNewLine();
        } else {
            showBase = left == null && right == null;
        }

        if (left != null) {
            int len = Math.min(left.high, left.doc.getElements().length);
            for (int i = left.low; i < len; i++) {
                out.write(left.doc.getElements()[i].getString());
            }
        }
        if (showBase) {
            if (conflict) {
                out.write(getMarker(MARKER_B, base.doc));
                out.writeNewLine();
            }
            int len = Math.min(base.high, base.doc.getElements().length);
            for (int i = base.low; i < len; i++) {
                out.write(base.doc.getElements()[i].getString());
            }
        }
        if (conflict) {
            out.write(getMarker(MARKER_M, null));
            out.writeNewLine();
        }
        if (right != null) {
            int len = Math.min(right.high, right.doc.getElements().length);
            for (int i = right.low; i < len; i++) {
                out.write(right.doc.getElements()[i].getString());
            }
        }
        if (conflict) {
            out.write(getMarker(MARKER_R, right.doc));
            out.writeNewLine();
        }
    }

    /**
     * generate a debug string
     * @return a debug string
     */
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("@@ =").append(base).append(" -").append(left).append(" +").append(right).append(" @@\n");
        if (left != null) {
            for (int i = 0; i < left.len(); i++) {
                addLineNumbers(buf, i);
                buf.append("< ");
                buf.append(left.doc.getElements()[i + left.low]);
            }
        }
        if (base != null) {
            for (int i = 0; i < base.len(); i++) {
                addLineNumbers(buf, i);
                buf.append("= ");
                buf.append(base.doc.getElements()[i + base.low]);
            }
        }
        if (right != null) {
            for (int i = 0; i < right.len(); i++) {
                addLineNumbers(buf, i);
                buf.append("> ");
                buf.append(right.doc.getElements()[i + right.low]);
            }
        }
        return buf.toString();
    }

    /**
     * adds some line numbers to the string buffer
     * @param buf the buffer
     * @param i the index
     */
    private void addLineNumbers(StringBuffer buf, int i) {
        buf.append("(").append(i + base.low);
        if (left != null) {
            buf.append(",").append(i + left.low);
        }
        if (right != null) {
            buf.append(",").append(i + right.low);
        }
        buf.append(") ");
    }

    /**
     * Returns the marker string for the given format and document
     * @param fmt the marker format
     * @param doc the document or <code>null</code>.
     * @return the marker string
     */
    public static String getMarker(String[] fmt, Document doc) {
        StringBuffer buf = new StringBuffer(fmt[0]);
        if (doc != null && doc.getSource() != null && doc.getSource().getLabel() != null) {
            buf.append(doc.getSource().getLabel());
        } else {
            buf.append(fmt[1]);
        }
        return buf.toString();
    }

}