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
 * Specifies a range in a document
 */
public class Range {

    /**
     * the document this range points to
     */
    public final Document doc;

    /**
     * the low line of this range
     */
    public final int low;

    /**
     * the high line of this range. actually points to the line after the one
     * included in this range.
     */
    public final int high;

    /**
     * Creates a new range
     * @param doc the document
     * @param low the low line
     * @param high the high line
     */
    public Range(Document doc, int low, int high) {
        assert low >= 0;
        assert high >= low;
        this.doc = doc;
        this.low = low;
        this.high = high;
    }

    /**
     * Returns the length of this range
     * @return the length.
     */
    public int len() {
        return high - low;
    }

    /**
     * Returns a debug string
     * @return a debug string
     */
    public String toString() {
        return low + "-" + high;
    }

    /**
     * Returns a string suitable for inclusion in a diff output (line number is
     * incremented by one).
     * @return a string for output
     */
    public String toRangeString() {
        if (len() == 1) {
            return String.valueOf(low+1);
        } else {
            return (low + 1) + "," + len();
        }
    }
}