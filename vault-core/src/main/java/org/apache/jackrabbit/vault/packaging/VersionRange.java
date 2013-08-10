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

package org.apache.jackrabbit.vault.packaging;

/**
 * Implements a version range
 * @since 2.0
 */
public class VersionRange {

    /**
     * Infinite (covers all) range.
     */
    public static final VersionRange INFINITE = new VersionRange(null, true, null, true);

    /**
     * lower bound
     */
    private final Version low;

    /**
     * specifies if lower bound is inclusive
     */
    private final boolean lowIncl;

    /**
     * upper bound
     */
    private final Version high;

    /**
     * specifies if upper bound is inclusive
     */
    private final boolean highIncl;

    /**
     * internal string representation
     */
    private final String str;

    /**
     * Creates a new version range.
     * @param low lower bound or <code>null</code>
     * @param lowIncl specifies if lower bound is inclusive
     * @param high upper bound or <code>null</code>
     * @param highIncl specifies if upper bound is inclusive
     * @throws IllegalArgumentException if bounds are not valid
     */
    public VersionRange(Version low, boolean lowIncl, Version high, boolean highIncl) {
        // check if range is valid
        if (low != null && high != null) {
            int comp = low.compareTo(high);
            if (comp > 0) {
                throw new IllegalArgumentException("lower bound must be less or equal to upper bound.");
            } else if (comp == 0) {
                if (!lowIncl || !highIncl) {
                    throw new IllegalArgumentException("invalid empty range. upper and lower bound must be inclusive.");
                }
            }
        }
        this.low = low;
        this.lowIncl = lowIncl;
        this.high = high;
        this.highIncl = highIncl;
        StringBuilder b = new StringBuilder();
        if (low == null && high == null) {
            // infinite range, empty string
        } else if (high == null) {
            // no high bound,
            if (lowIncl) {
                // special case, just use version
                b.append(low);
            } else {
                b.append('(');
                b.append(low);
                b.append(",)");
            }
        } else if (low == null) {
            b.append("[,");
            b.append(high);
            b.append(highIncl ? ']' : ')');
        } else {
            b.append(lowIncl ? '[' : '(');
            b.append(low);
            b.append(',');
            b.append(high);
            b.append(highIncl ? ']' : ')');
        }
        this.str = b.toString();
    }

    /**
     * Creates a new version range that exactly includes the given version.
     * @param v the version.
     */
    public VersionRange(Version v) {
        this(v, true, v, true);
    }

    /**
     * Returns the lower bound
     * @return the lower bound or <code>null</code>
     */
    public Version getLow() {
        return low;
    }

    /**
     * Returns <code>true</code> if the lower bound is inclusive
     * @return <code>true</code> if the lower bound is inclusive
     */
    public boolean isLowInclusive() {
        return lowIncl;
    }

    /**
     * Returns the upper bound
     * @return the upper bound or <code>null</code>
     */
    public Version getHigh() {
        return high;
    }

    /**
     * Returns <code>true</code> if the upper bound is inclusive
     * @return <code>true</code> if the upper bound is inclusive
     */
    public boolean isHighInclusive() {
        return highIncl;
    }

    @Override
    public int hashCode() {
        return str.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj ||
                obj instanceof VersionRange && str.equals(obj.toString());
    }

    @Override
    public String toString() {
        return str;
    }

    /**
     * Checks if the given version is in this range.
     * @param v the version to check
     * @return <code>true</code> if the given version is in this range.
     */
    public boolean isInRange(Version v) {
        if (low != null) {
            int comp = v.osgiCompareTo(low);
            if (comp < 0 || comp == 0 && !lowIncl) {
                return false;
            }
        }
        if (high != null) {
            int comp = v.osgiCompareTo(high);
            if (comp > 0 || comp == 0 && !highIncl) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a range from a string
     * @param str string
     * @return the version range
     */
    public static VersionRange fromString(String str) {
        int idx = str.indexOf(',');
        if (idx >= 0) {
            boolean linc = false;
            int lm = str.indexOf('(');
            if (lm < 0) {
                lm = str.indexOf('[');
                if (lm < 0) {
                    throw new IllegalArgumentException("Range must start with '[' or '('");
                }
                linc = true;
            }
            boolean hinc = false;
            int hm = str.indexOf(')');
            if (hm < 0) {
                hm = str.indexOf(']');
                if (hm < 0) {
                    throw new IllegalArgumentException("Range must end with ']' or ')'");
                }
                hinc = true;
            }
            String low = str.substring(lm + 1, idx).trim();
            String high = str.substring(idx+1, hm).trim();
            Version vLow = low.length() == 0 ? null : Version.create(low);
            Version vHigh = high.length() == 0 ? null : Version.create(high);
            return new VersionRange(vLow, linc, vHigh, hinc);
        } else if (str.length() == 0) {
            // infinite range
            return new VersionRange(null, false, null, false);
        } else {
            // simple range where given version is minimum
            return new VersionRange(Version.create(str), true, null, false);
        }
    }
}