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
import java.util.List;

/**
 * Implements a package dependency reference.
 * @since 2.0
 */
public class Dependency {

    /**
     * An empty dependency array
     */
    public static final Dependency[] EMPTY = new Dependency[0];

    /**
     * group id of the dependency
     */
    private final String groupId;

    /**
     * name of the dependency
     */
    private final String name;

    /**
     * Version range of the dependency
     */
    private final VersionRange range;

    /**
     * internal string representation
     */
    private final String str;

    /**
     * Creates a new dependency reference.
     * @param groupId group id
     * @param name name
     * @param range version range
     */
    public Dependency(String groupId, String name, VersionRange range) {
        if (groupId.startsWith(PackageId.ETC_PACKAGES_PREFIX)) {
            groupId = groupId.substring(PackageId.ETC_PACKAGES_PREFIX.length());
        }
        this.groupId = groupId;
        this.name = name;
        this.range = range == null ? VersionRange.INFINITE : range;
        StringBuilder b = new StringBuilder();
        if (groupId.length() > 0 || !VersionRange.INFINITE.equals(this.range)) {
            b.append(groupId);
            b.append(":");
        }
        b.append(name);
        if (!VersionRange.INFINITE.equals(this.range)) {
            b.append(":");
            b.append(range);
        }
        this.str = b.toString();
    }

    /**
     * Creates a new dependency to the specified package id
     * @param id package id.
     */
    public Dependency(PackageId id) {
        this(id.getGroup(), id.getName(), new VersionRange(id.getVersion()));
    }

    /**
     * Returns the group of the dependency
     * @return the group id
     * @since 2.4
     */
    public String getGroup() {
        return groupId;
    }

    /**
     * Returns the name of the dependency
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the version range
     * @return the version range
     */
    public VersionRange getRange() {
        return range;
    }

    /**
     * Returns the installation path of this dependency
     * @return the installation path;
     */
    public String getPath() {
        StringBuilder b = new StringBuilder();
        if (groupId.length() > 0) {
            if (groupId.charAt(0) != '/') {
                b.append(PackageId.ETC_PACKAGES_PREFIX);
            }
            b.append(groupId);
            b.append("/");
        } else {
            b.append(PackageId.ETC_PACKAGES_PREFIX);
        }
        b.append(name);
        return b.toString();
    }

    /**
     * Checks if the given package id matches this dependency specification.
     * @param id the package id
     * @return <code>true</code> if matches
     */
    public boolean matches(PackageId id) {
        return groupId.equals(id.getGroup())
                && name.equals(id.getName())
                && range.isInRange(id.getVersion());
    }

    /**
     * Returns a dependency from a string. if the given id is null or an
     * empty string, <code>null</code> is returned.
     * @param str the string
     * @return the dependency
     */
    public static Dependency fromString(String str) {
        if (str == null || str.length() == 0) {
            return null;
        }
        String[] segs = str.split(":");
        String name;
        String groupId = "";
        String range = null;
        if (segs.length == 1) {
            name = segs[0];
            // be backward compatible, respect group in name
            int idx = name.lastIndexOf('/');
            if (idx >= 0) {
                groupId = name.substring(0, idx);
                name = name.substring(idx + 1);
            }
        } else if (segs.length == 2) {
            groupId = segs[0];
            name = segs[1];
            boolean isVersion = true;
            if (name.length() > 0) {
                char c = name.charAt(0);
                isVersion = Character.isDigit(c) || c == '[' || c == ')';
            }
            // be backward compatible, respect group in name
            int idx = name.lastIndexOf('/');
            if (idx >= 0 && groupId.length() == 0) {
                groupId = name.substring(0, idx);
                name = name.substring(idx + 1);
            } else if ((idx = groupId.lastIndexOf('/')) >=0 && isVersion) {
                groupId = segs[0].substring(0, idx);
                name = segs[0].substring(idx + 1);
                range = segs[1];
            }
        } else {
            groupId = segs[0];
            name = segs[1];
            range = segs[2];
        }
        return new Dependency(groupId, name, range == null ? null : VersionRange.fromString(range));
    }

    /**
     * Parses a string serialization of dependency references generated by
     * {@link #toString(Dependency...)})}
     *
     * @param str serialized string
     * @return array of dependency references
     */
    public static Dependency[] parse(String str) {
        List<Dependency> deps = new ArrayList<Dependency>();
        boolean inRange = false;
        int start = 0;
        boolean wasSeg = false;
        for (int i=0; i<str.length(); i++) {
            char c = str.charAt(i);
            if (c == ',') {
                if (!inRange) {
                    deps.add(Dependency.fromString(str.substring(start, i)));
                    start = i + 1;
                }
            } else if (c == '[' || c == '(') {
                if (wasSeg) {
                    inRange = true;
                }
            } else if (c == ']' || c == ')') {
                inRange = false;
            }
            wasSeg = c == ':';
        }
        if (start < str.length()) {
            deps.add(Dependency.fromString(str.substring(start)));
        }
        return deps.toArray(new Dependency[deps.size()]);
    }
    
    /**
     * Returns dependencies from the given strings.
     * @param str the strings
     * @return the dependencies
     */
    public static Dependency[] fromString(String ... str) {
        Dependency[] ret = new Dependency[str.length];
        for (int i=0; i<str.length;i++) {
            ret[i] = Dependency.fromString(str[i]);
        }
        return ret;
    }

    /**
     * Returns a string representation from given dependencies.
     * @param deps the dependencies
     * @return the strings
     */
    public static String toString(Dependency ... deps) {
        String delim = "";
        StringBuilder b = new StringBuilder();
        for (Dependency dep: deps) {
            b.append(delim).append(dep);
            delim=",";
        }
        return b.toString();
    }

    @Override
    public String toString() {
        return str;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj ||
                obj instanceof Dependency && str.equals(obj.toString());
    }

    @Override
    public int hashCode() {
        return str.hashCode();
    }

}