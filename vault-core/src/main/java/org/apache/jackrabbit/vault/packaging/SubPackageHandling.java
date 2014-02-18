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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.jackrabbit.util.Text;

/**
 * The sub package handling specifies how sub package are handled during recursive package installation. This
 * configuration consists of a list of {@link org.apache.jackrabbit.vault.packaging.SubPackageHandling.Entry}s that
 * match against a given {@link org.apache.jackrabbit.vault.packaging.PackageId}. The version of the package id is
 * ignored.
 * <p/>
 * The sub package handling can be specified in the package properties as a string of the following format:
 * <xmp>
 *     subPackageHandling := instruction { "," instruction };
 *     instruction := packageIdFilter { ";" option }
 *     packageIdFilter := packageNameFilter | groupNameFilter ":" packageNameFilter;
 *     groupNameFilter := "*" | groupName;
 *     packageNameFilter := "*" | packageName;
 *     option := "install" | "extract" | "add" | "ignore";
 * </xmp>
 *
 *  Note that 'ignore' is currently not really supported as sub packages are part of the normal package content and
 *  behaves the same as 'add'. Future implementations will transport the sub packages outside of the normal package
 *  content, e.g. in a META-INF/vault/subpackages/ folder (see JCRVLT-33).
 */
public class SubPackageHandling {

    /**
     * The sub package option
     */
    public enum Option {
        /**
         * adds and installs the package using {@link JcrPackage#install(org.apache.jackrabbit.vault.fs.io.ImportOptions)}
         */
        INSTALL,

        /**
         * adds and extracts the package using {@link JcrPackage#extract(org.apache.jackrabbit.vault.fs.io.ImportOptions)}
         */
        EXTRACT,

        /**
         * adds the package using {@link JcrPackageManager#upload}
         */
        ADD,

        /**
         * ignores the sub package completely
         */
        IGNORE
    }

    public static class Entry {

        private final String groupName;

        private final String packageName;

        private final Option option;

        public Entry(String groupName, String packageName, Option option) {
            this.groupName = groupName == null || groupName.isEmpty() ? "*" : groupName;
            this.packageName = packageName == null || packageName.isEmpty() ? "*" : packageName;
            this.option = option;
        }

        public String getGroupName() {
            return groupName;
        }

        public String getPackageName() {
            return packageName;
        }

        public Option getOption() {
            return option;
        }
    }

    /**
     * The default handling
     */
    public static final SubPackageHandling DEFAULT = new SubPackageHandling(Collections.<Entry>emptyList());

    private final List<Entry> entries;

    public SubPackageHandling() {
        this(new LinkedList<Entry>());
    }

    private SubPackageHandling(List<Entry> entries) {
        this.entries = entries;
    }

    /**
     * Parses a options string as described above and returns a new SubPackageHandling instance.
     * @param str the string to parse
     * @return the configuration or {@code null} if the string is malformed.
     */
    public static SubPackageHandling fromString(String str) {
        if (str == null || str.isEmpty()) {
            return SubPackageHandling.DEFAULT;
        }
        SubPackageHandling sp = new SubPackageHandling();
        for (String instruction: Text.explode(str, ',')) {
            String[] opts = Text.explode(instruction.trim(), ';');
            if (opts.length >  0) {
                PackageId id = PackageId.fromString(opts[0]);
                Option opt = Option.INSTALL;
                if (opts.length > 1) {
                    try {
                        opt = Option.valueOf(opts[1].toUpperCase());
                    } catch (IllegalArgumentException e) {
                        // ignore
                    }
                }
                sp.getEntries().add(new Entry(id.getGroup(), id.getName(), opt));
            }
        }
        return sp;
    }

    /**
     * Gets the option from the entries list that matches the package last. If no entry match, it returns
     * {@link org.apache.jackrabbit.vault.packaging.SubPackageHandling.Option#INSTALL}
     * @param id the package id to match
     * @return the option.
     */
    public Option getOption(PackageId id) {
        Option opt = null;
        for (Entry e: entries) {
            if (!"*".equals(e.groupName) && !id.getGroup().equals(e.groupName)) {
                continue;
            }
            if (!"*".equals(e.packageName) && !id.getName().equals(e.packageName)) {
                continue;
            }
            opt = e.option;
        }
        return opt == null ? Option.INSTALL : opt;
    }

    /**
     * Returns the modifiable list of entries.
     * @return the list of entries
     */
    public List<Entry> getEntries() {
        return entries;
    }

    /**
     * Returns the parseable string representation of this configuration.
     * @return the string representation.
     */
    public String getString() {
        StringBuilder sb = new StringBuilder();
        for (Entry e: entries) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(e.getGroupName()).append(":").append(e.getPackageName());
            if (e.option != Option.INSTALL) {
                sb.append(';').append(e.option.toString().toLowerCase());
            }
        }
        return sb.toString();
    }
}