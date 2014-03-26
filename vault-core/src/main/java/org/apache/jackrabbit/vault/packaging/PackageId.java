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

import org.apache.jackrabbit.util.XMLChar;
import org.apache.jackrabbit.vault.util.Text;

/**
 * <code>PackageId</code> provides the basic metrics for identifying a package.
 * A package id consists of a group id, a name and a version.
 * the group is a relative path, eg: "company/project/subgroup", the name and the version
 * can be of any format.
 */
public class PackageId implements Comparable<PackageId> {

    public static final String ETC_PACKAGES = "/etc/packages";

    public static final String ETC_PACKAGES_PREFIX = "/etc/packages/";

    public static final PackageId[] EMPTY = new PackageId[0];
    
    private final String group;

    private final String name;

    private final Version version;

    private final String str;

    private final boolean fromPath;

    /**
     * Creates a new package id
     * @param path path of the package
     */
    public PackageId(String path) {
        fromPath = true;
        path = path.trim();
        int idx = path.lastIndexOf('.');
        if (idx > 0) {
            String ext = path.substring(idx);
            if (ext.equalsIgnoreCase(".zip") || ext.equalsIgnoreCase(".jar")) {
                path = path.substring(0, idx);
            }
        }
        idx = path.lastIndexOf('/');
        String name;
        if (idx < 0) {
            name = path;
            this.group = "";
        } else {
            name = path.substring(idx + 1);
            String grp = path.substring(0, idx);
            if (grp.equals(ETC_PACKAGES)) {
                grp = "";
            } else if (grp.startsWith(ETC_PACKAGES_PREFIX)) {
                grp = grp.substring(ETC_PACKAGES_PREFIX.length());
            } else if (grp.startsWith("/")) {
                grp = grp.substring(1);
            }
            this.group = grp;
        }
        // check if name contains a version
        String[] segs = Text.explode(name, '-');
        int i=segs.length-1;
        while (i>0) {
            try {
                // accept numbers < 1000 (hotfix case)
                if (Integer.parseInt(segs[i]) >= 1000) {
                    break;
                }
            } catch (NumberFormatException e) {
                // ignore
            }
            // check if starts with a letter'
            if (Character.isJavaIdentifierStart(segs[i].charAt(0))) {
                // then need a digit
                if (segs[i].length() == 1 || !Character.isDigit(segs[i].charAt(1)) && !segs[i].equals("SNAPSHOT")) {
                    break;
                }
            }
            i--;
        }
        if (i == segs.length-1) {
            this.name = name;
            version = Version.EMPTY;
        } else {
            StringBuilder str = new StringBuilder();
            for (int j = 0; j<= i; j++) {
                if (j > 0) {
                    str.append('-');
                }
                str.append(segs[j]);
            }
            this.name = str.toString();
            str.setLength(0);
            for (int j = i+1; j<segs.length; j++) {
                if (j > i+1) {
                    str.append('-');
                }
                str.append(segs[j]);
            }
            this.version = Version.create(str.toString());
        }
        this.str = getString(group, this.name, version);
    }

    /**
     * Creates a new package id
     * @param path path of the package
     * @param version version of the package
     */
    public PackageId(String path, String version) {
        this(path, Version.create(version));
    }

    /**
     * Creates a new package id
     * @param path path of the package
     * @param version version of the package
     */
    public PackageId(String path, Version version) {
        fromPath = true;
        path = path.trim();
        int idx = path.lastIndexOf('.');
        if (idx > 0) {
            String ext = path.substring(idx);
            if (ext.equalsIgnoreCase(".zip") || ext.equalsIgnoreCase(".jar")) {
                path = path.substring(0, idx);
            }
        }
        if (version != null && path.endsWith('-'+version.toString())) {
            path = path.substring(0, path.length() - version.toString().length() - 1);
        }
        idx = path.lastIndexOf('/');
        if (idx < 0) {
            this.name = path;
            this.group = "";
        } else {
            this.name = path.substring(idx + 1);
            String grp = path.substring(0, idx);
            if (grp.equals(ETC_PACKAGES)) {
                grp = "";
            } else if (grp.startsWith(ETC_PACKAGES_PREFIX)) {
                grp = grp.substring(ETC_PACKAGES_PREFIX.length());
            } else if (grp.startsWith("/")) {
                grp = grp.substring(1);
            }
            this.group = grp;
        }
        // sanitize version
        if (version == null || version.toString().length() == 0) {
            version = Version.EMPTY;
        }
        this.version = version;
        this.str = getString(group, name, version);
    }

    /**
     * Creates a new package id
     * @param group group id
     * @param name name
     * @param version version
     */
    public PackageId(String group, String name, String version) {
        this(group, name, Version.create(version));
    }

    /**
     * Creates a new package id
     * @param group group id
     * @param name name
     * @param version version
     */
    public PackageId(String group, String name, Version version) {
        fromPath = false;
        // validate group
        if (group.equals(ETC_PACKAGES)) {
            group = "";
        } else if (group.startsWith(ETC_PACKAGES_PREFIX)) {
            group = group.substring(ETC_PACKAGES_PREFIX.length());
        } else if (group.startsWith("/")) {
            group = group.substring(1);
        }
        this.group = group;
        this.name = name;
        this.version = version == null ? Version.EMPTY : version;
        this.str = getString(this.group, name, this.version);
    }

    /**
     * Returns a package id from a id string. if the given id is null or an
     * empty string, <code>null</code> is returned.
     * @param str the string
     * @return the package id
     */
    public static PackageId fromString(String str) {
        if (str == null || str.length() == 0) {
            return null;
        }
        String[] segs = str.split(":");
        if (segs.length == 1) {
            return new PackageId("", segs[0], "");
        } else if (segs.length == 2) {
            return new PackageId(segs[0], segs[1], "");
        } else {
            return new PackageId(segs[0], segs[1], segs[2]);
        }
    }

    /**
     * Returns an array of package id from strings
     * @param str the strings
     * @return the array of package ids
     */
    public static PackageId[] fromString(String ... str) {
        PackageId[] ret = new PackageId[str.length];
        for (int i=0; i<str.length; i++) {
            ret[i] = PackageId.fromString(str[i]);
        }
        return ret;
    }

    /**
     * Creates a comma separated list of id strings.
     * @param packs the ids
     * @return the string
     */
    public static String toString(PackageId ... packs) {
        String delim = "";
        StringBuilder b = new StringBuilder();
        for (PackageId pack: packs) {
            b.append(delim).append(pack);
            delim=",";
        }
        return b.toString();
    }

    /**
     * Checks if this definition was constructed from a path, rather from a
     * group and name.
     * @return <code>true</code> if constructed from path.
     *
     * @since 2.2.26
     */
    public boolean isFromPath() {
        return fromPath;
    }

    /**
     * Returns the path of this package. please note that since 2.3 this also
     * includes the version, but never the extension (.zip).
     *
     * @return the path of this package
     * @since 2.2
     */
    public String getInstallationPath() {
        StringBuilder b = new StringBuilder(ETC_PACKAGES_PREFIX);
        if (group.length() > 0) {
            b.append(group);
            b.append("/");
        }
        b.append(name);
        if (version.toString().length() > 0) {
            b.append("-").append(version);
        }
        return b.toString();
    }

    /**
     * Returns the group id of this package
     * @return the group id;
     * @since 2.2
     */
    public String getGroup() {
        return group;
    }

    /**
     * Returns the name of this package which is the last segment of the path.
     * @return the name of this package.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the version of this package or and empty string if n/a.
     * @return the version of this package
     * @since 2.0
     */
    public String getVersionString() {
        return version.toString();
    }

    /**
     * Returns a download name in the form
     * <code>name [ "-" version ] ".zip"</code>
     * @return the download name
     * @since 2.0
     */
    public String getDownloadName() {
        StringBuilder str = new StringBuilder(name);
        if (version.toString().length() > 0) {
            str.append("-").append(version);
        }
        str.append(".zip");
        return str.toString();
    }

    /**
     * Returns the version of this package or <code>null</code> if n/a.
     * @return the version of this package
     */
    public Version getVersion() {
        return version;
    }

    /**
     * Returns a string representation of this id
     */
    @Override
    public String toString() {
        return str;
    }

    @Override
    public boolean equals(Object o) {
        return this == o ||
                o instanceof PackageId && str.equals(o.toString());

    }

    @Override
    public int hashCode() {
        return str.hashCode();
    }

    /**
     * {@inheritDoc}
     *  
     * Compares this id with the given one.
     */
    public int compareTo(PackageId o) {
        int comp = group.compareTo(o.getGroup());
        if (comp != 0) {
            return comp;
        }
        comp = name.compareTo(o.getName());
        if (comp != 0) {
            return comp;
        }
        return version.compareTo(o.getVersion());
    }

    /**
     * Internally get the string representation, colon separated.
     * @param group group name
     * @param name name
     * @param version version
     * @return string version
     */
    private static String getString(String group, String name, Version version) {
        return getString(group, name, version == null ? "" : version.toString());
    }

    /**
     * Internally get the string representation, colon separated.
     * @param group group name
     * @param name name
     * @param version version
     * @return string version
     */
    private static String getString(String group, String name, String version) {
        StringBuilder b = new StringBuilder();
        b.append(group).append(':');
        b.append(name);
        if (version.length() > 0) {
            b.append(':').append(version);
        }
        return b.toString();
    }

    /**
     * Checks if this package id is valid in respect to JCR names.
     * @return {@code true} if the names are valid
     */
    public boolean isValid() {
        return PackageId.isValid(group, name, version == null ? null : version.toString());
    }

    /**
     * Checks if the package id is valid in respect to JCR names.
     * @param group the package group name
     * @param name the package name
     * @param version the (optional) version
     * @return {@code true} if the names are valid
     */
    public static boolean isValid(String group, String name, String version) {
        try {
            assertValidJcrName(name);
            if (version != null && !version.isEmpty()) {
                assertValidJcrName(version);
            }
            for (String groupSegment: Text.explode(group, '/')) {
                assertValidJcrName(groupSegment);
            }
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // the code below is copied from org.apache.jackrabbit.spi.commons.conversion.NameParser

    // constants for parser
    private static final int STATE_PREFIX_START = 0;
    private static final int STATE_PREFIX = 1;
    private static final int STATE_NAME_START = 2;
    private static final int STATE_NAME = 3;
    private static final int STATE_URI_START = 4;
    private static final int STATE_URI = 5;

    /**
     * Parses the <code>jcrName</code> (either qualified or expanded) and validates it.
     * @throws java.lang.IllegalArgumentException if the name is not valid
     */
    private static void assertValidJcrName(String jcrName) throws IllegalArgumentException {
        // trivial check
        int len = jcrName == null ? 0 : jcrName.length();
        if (len == 0) {
            throw new IllegalArgumentException("empty name");
        }
        if (".".equals(jcrName) || "..".equals(jcrName)) {
            throw new IllegalArgumentException(jcrName);
        }

        // parse the name
        String prefix;
        int nameStart = 0;
        int state = STATE_PREFIX_START;
        boolean trailingSpaces = false;

        for (int i = 0; i < len; i++) {
            char c = jcrName.charAt(i);
            if (c == ':') {
                if (state == STATE_PREFIX_START) {
                    throw new IllegalArgumentException("Prefix must not be empty");
                } else if (state == STATE_PREFIX) {
                    if (trailingSpaces) {
                        throw new IllegalArgumentException("Trailing spaces not allowed");
                    }
                    prefix = jcrName.substring(0, i);
                    if (!XMLChar.isValidNCName(prefix)) {
                        throw new IllegalArgumentException("Invalid name prefix: "+ prefix);
                    }
                    state = STATE_NAME_START;
                } else if (state == STATE_URI) {
                    // ignore -> validation of uri later on.
                } else {
                    throw new IllegalArgumentException("'" + c + "' not allowed in name");
                }
                trailingSpaces = false;
            } else if (c == ' ') {
                if (state == STATE_PREFIX_START || state == STATE_NAME_START) {
                    throw new IllegalArgumentException("'" + c + "' not valid name start");
                }
                trailingSpaces = true;
            } else if (Character.isWhitespace(c) || c == '[' || c == ']' || c == '*' || c == '|') {
                throw new IllegalArgumentException("'" + c + "' not allowed in name");
            } else if (c == '/') {
                if (state == STATE_URI_START) {
                    state = STATE_URI;
                } else if (state != STATE_URI) {
                    throw new IllegalArgumentException("'" + c + "' not allowed in name");
                }
                trailingSpaces = false;
            } else if (c == '{') {
                if (state == STATE_PREFIX_START) {
                    state = STATE_URI_START;
                } else if (state == STATE_URI_START || state == STATE_URI) {
                    // second '{' in the uri-part -> no valid expanded jcr-name.
                    // therefore reset the nameStart and change state.
                    state = STATE_NAME;
                    nameStart = 0;
                } else if (state == STATE_NAME_START) {
                    state = STATE_NAME;
                    nameStart = i;
                }
                trailingSpaces = false;
            } else if (c == '}') {
                if (state == STATE_URI_START || state == STATE_URI) {
                    String tmp = jcrName.substring(1, i);
                    if (tmp.length() == 0 || tmp.indexOf(':') != -1) {
                        // The leading "{...}" part is empty or contains
                        // a colon, so we treat it as a valid namespace URI.
                        // More detailed validity checks (is it well formed,
                        // registered, etc.) are not needed here.
                        state = STATE_NAME_START;
                    } else if (tmp.equals("internal")) {
                        // As a special Jackrabbit backwards compatibility
                        // feature, support {internal} as a valid URI prefix
                        state = STATE_NAME_START;
                    } else if (tmp.indexOf('/') == -1) {
                        // The leading "{...}" contains neither a colon nor
                        // a slash, so we can interpret it as a a part of a
                        // normal local name.
                        state = STATE_NAME;
                        nameStart = 0;
                    } else {
                        throw new IllegalArgumentException(
                                "The URI prefix of the name " + jcrName
                                        + " is neither a valid URI nor a valid part"
                                        + " of a local name.");
                    }
                } else if (state == STATE_PREFIX_START) {
                    state = STATE_PREFIX; // prefix start -> validation later on will fail.
                } else if (state == STATE_NAME_START) {
                    state = STATE_NAME;
                    nameStart = i;
                }
                trailingSpaces = false;
            } else {
                if (state == STATE_PREFIX_START) {
                    state = STATE_PREFIX; // prefix start
                } else if (state == STATE_NAME_START) {
                    state = STATE_NAME;
                    nameStart = i;
                } else if (state == STATE_URI_START) {
                    state = STATE_URI;
                }
                trailingSpaces = false;
            }
        }

        // take care of qualified jcrNames starting with '{' that are not having
        // a terminating '}' -> make sure there are no illegal characters present.
        if (state == STATE_URI && (jcrName.indexOf(':') > -1 || jcrName.indexOf('/') > -1)) {
            throw new IllegalArgumentException("Local name may not contain ':' nor '/'");
        }

        if (nameStart == len || state == STATE_NAME_START) {
            throw new IllegalArgumentException("Local name must not be empty");
        }
        if (trailingSpaces) {
            throw new IllegalArgumentException("Trailing spaces not allowed");
        }
    }
}