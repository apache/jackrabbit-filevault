/*************************************************************************
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
 ************************************************************************/
package org.apache.jackrabbit.vault.util.diff;

import java.util.Arrays;
import java.util.List;

import difflib.Chunk;
import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;

/**
 * A class to compare vectors of objects.
 */
public class Diff {

    /**
     * Left list of elements
     */
    private final List<?> left;

    /**
     * Right list of elements
     */
    private final List<?> right;


    /**
     * Constructor to find differences between two arrays.
     * @param a left "document"
     * @param b right document
     */
    public Diff(Object[] a, Object[] b) {
        left = Arrays.asList(a);
        right = Arrays.asList(b);
    }

    /**
     * When set to true, the comparison uses a heuristic to speed it up.
     * With this heuristic, for files with a constant small density
     * of changes, the algorithm is linear in the file size.
     */
    @Deprecated
    public boolean heuristic = false;

    /**
     * When set to true, the algorithm returns a guaranteed minimal
     * set of changes.  This makes things slower, sometimes much slower.
     */
    @Deprecated
    public boolean no_discards = false;

    /**
     * Compute the difference between the 2 arrays.
     */
    public Change diff_2(final boolean reverse) {
        Change prev = new Change(0,0,0,0, null);
        Change ret = prev;
        Patch p = DiffUtils.diff(left, right);

        // recompute the changes based on the deltas.
        // todo: use the deltas directly in the DocumentDiff.
        for (Delta d: p.getDeltas()) {
            Chunk c0 = d.getOriginal();
            Chunk c1 = d.getRevised();
            Change next = new Change(
                    c0.getPosition(), c1.getPosition(),
                    c0.getLines().size(), c1.getLines().size(),
                    null);

            if (reverse) {
                next.nextChange = ret;
                ret = next;
            } else {
                prev.nextChange = next;
                prev = next;
            }
        }
        return reverse ? ret : ret.nextChange;
    }

    /**
     * The result of the diff.
     */
    public static class Change {

        /**
         * Previous or next edit command.
         */
        public Change nextChange;

        /**
         * # lines of file 1 changed here.
         */
        public final int inserted;

        /**
         * # lines of file 0 changed here.
         */
        public final int deleted;

        /**
         * Line number of 1st deleted line.
         */
        public final int line0;

        /**
         * Line number of 1st inserted line.
         */
        public final int line1;

        /**
         * Creates a new change entry. If {@code deleted} is 0, then {@code line0} is the number of the line before the
         * insertion was done. If {@code inserted} is 0, then {@code line1} is the number of the line before the deletion
         * was done.
         *
         * @param line0 first affected line in the left file.
         * @param line1 first affected line in the right file.
         * @param deleted number of deleted lines from left file.
         * @param inserted number of inserted lines in right file
         * @param old previous change.
         */
        Change(int line0, int line1, int deleted, int inserted, Change old) {
            this.line0 = line0;
            this.line1 = line1;
            this.inserted = inserted;
            this.deleted = deleted;
            this.nextChange = old;
        }
    }


}
