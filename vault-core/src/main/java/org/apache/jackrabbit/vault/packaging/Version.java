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

import java.util.ArrayList;

import org.apache.jackrabbit.util.Text;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implements a package version.
 * @since 2.0
 */
public class Version implements Comparable<Version> {

    /**
     * The empty version
     */
    public static final Version EMPTY = new Version("", new String[0]);

    /**
     * internal string representation
     */
    private final String str;

    /**
     * All segments of this version
     */
    private final String[] segments;

    /**
     * Creates a new version from the given string.
     *
     * @param str the version string.
     * @return the new version or {@link Version#EMPTY} if {@code str} is an empty string.
     * @since 2.2.4
     */
    @NotNull
    public static Version create(@Nullable String str) {
        if (str == null || str.length() == 0) {
            return Version.EMPTY;
        }
        return Version.create(Text.explode(str, '.'));
    }

    /**
     * Creates a new version from version segments
     * @param segments version segments
     * @return the new version or {@link Version#EMPTY} if {@code segments} is empty.
     * @since 2.2.4
     */
    @NotNull
    public static Version create(@Nullable String[] segments) {
        if (segments == null || segments.length == 0) {
            return Version.EMPTY;
        }
        ArrayList<String> segs = new ArrayList<String>(segments.length+1);
        StringBuilder str = new StringBuilder();
        boolean hasQualifier = false;
        for (String s: segments) {
            // reconstruct version string
            if (str.length() > 0) {
                str.append('.');
            }
            str.append(s);

            // split first qualifier
            if (hasQualifier) {
                segs.add(s);
            } else {
                int dash = s.indexOf('-');
                if (dash < 0) {
                    segs.add(s);
                } else if (dash > 0) {
                    hasQualifier = true;
                    segs.add(s.substring(0, dash));
                    if (dash < s.length() - 1) {
                        segs.add(s.substring(dash + 1));
                    }
                }
            }
        }
        return new Version(str.toString(), segs.toArray(new String[segs.size()]));
    }

    /**
     * Internal constructor
     * @param str string
     * @param segments segments
     */
    private Version(@NotNull String str, @NotNull String[] segments) {
        this.str = str;
        this.segments = segments;
    }

    @Override
    public int hashCode() {
        return str.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return this == o ||
                o instanceof Version && str.equals(((Version) o).str);

    }

    @Override
    public String toString() {
        return str;
    }

    /**
     * Returns all segments.
     * @return all segments.
     */
    public String[] getNormalizedSegments() {
        return segments;
    }

    /**
     * Compares this version to the given one. The comparison is compatible to the ordering used by
     * <a href="https://maven.apache.org/">Apache Maven</a>. It version consists normally from 3 numbers -
     * major version, minor version and patch level, and can be followed by a dash and a qualifier like SNAPSHOT.
     * Version numbers can also consist of fewer or more numbers.
     * If the comparison is not resolved by comparing the numbers, the algorith resorts to the qualifier - see
     * <a href="https://cwiki.apache.org/confluence/display/MAVENOLD/Versioning">"Versioning" on Maven Wiki</a>
     * for details.
     *
     * <pre>
     * Some examples:
     * "1" &lt; "2"
     * "1.0" &lt; "2"
     * "2.0.1" &lt; "2.1"
     * "2.1" &lt; "2.1.1"
     * "2.9" &lt; "2.11"
     * "2.1-SNAPSHOT" &lt; "2.1"
     * "2.1-RC1" &lt; "2.1"
     * "2.1-RC1" &lt; "2.1-SNAPSHOT"
     * </pre>
     *
     * Please note that this comparison does not use the exact segmentation presented in {@link #getNormalizedSegments()},
     * but applies the maven comparison algorithm to the string representation {@link #toString()}.
     *
     * @param o the other version, not null
     * @return  a negative integer, zero, or a positive integer as this version
     *		is less than, equal to, or greater than the specified version.
     *
     * @see "https://cwiki.apache.org/confluence/display/MAVENOLD/Versioning"
     * @see "https://semver.org/spec/v1.0.0.html"
     */
    public int compareTo(@NotNull Version o) {
        ComparableVersion thisVersion = new ComparableVersion(toString());
        ComparableVersion otherVersion = new ComparableVersion(o.toString());
        return thisVersion.compareTo(otherVersion);
    }

    /**
     * Same as with {@link #compareTo(Version)}.
     * 
     * @param o the other version
     * @return  a negative integer, zero, or a positive integer as this version is less than, equal to, or greater than the specified version.
     * @deprecated since 3.1.32. use {@link #compareTo(Version)}. See JCRVLT-146
     */
    @Deprecated
    public int osgiCompareTo(Version o) {
        return compareTo(o);
    }
}
