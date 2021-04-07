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

package org.apache.jackrabbit.vault.fs.api;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The item filter set holds a set of item filters each attributed as include
 * or exclude filter. The evaluation of the set allows included items and
 * rejects excluded items.
 * <p>
 * Additionally it contains a "root" path for which the filters are evaluated.
 * if an item has not the node addressed by the root path as ancestor, it is
 * always excluded.
 */
public abstract class FilterSet<E extends Filter> implements Dumpable {

    /**
     * root path of this definition
     */
    @NotNull
    private String root;

    /**
     * root patten to check for inclusion
     */
    @NotNull
    private String rootPattern;

    /**
     * filter entries
     */
    @Nullable
    private List<Entry<E>> entries;

    /**
     * flag that indicates if set is sealed
     */
    private boolean sealed;

    /**
     * import mode. defaults to {@link ImportMode#REPLACE}.
     */
    @NotNull
    private ImportMode mode = ImportMode.REPLACE;

    /**
     * Default constructor. initializes the root path to "/"
     */
    public FilterSet() {
        this("");
    }

    /**
     * Creates a new item filter set and sets the respective root path
     * @param root path
     */
    public FilterSet(String root) {
        setRoot(root);
    }

    /**
     * Returns the root path
     * @return root path
     */
    @NotNull
    public String getRoot() {
        return "".equals(root) ? "/" : root;
    }

    /**
     * Sets the root path
     * @param path root path
     */
    public void setRoot(@NotNull String path) {
        if (sealed) {
            throw new UnsupportedOperationException("FilterSet is sealed.");
        }
        if (path.endsWith("/")) {
            rootPattern = path;
            root = path.substring(0, path.length() - 1);
        } else {
            rootPattern = path + "/";
            root = path;
        }
    }

    /**
     * Returns the import mode that is specified for this filter set. Defaults to
     * {@link ImportMode#REPLACE}.
     *
     * @return the import mode.
     */
    @NotNull
    public ImportMode getImportMode() {
        return mode;
    }

    /**
     * Sets the import mode.
     * @param mode import mode
     */
    public void setImportMode(@NotNull ImportMode mode) {
        if (sealed) {
            throw new UnsupportedOperationException("FilterSet is sealed.");
        }
        this.mode = mode;
    }

    /**
     * Seals this list, i.e. makes it unmodifiable.
     * @return this list
     */
    @NotNull
    public FilterSet seal() {
        if (!sealed) {
            if (entries == null) {
                entries = Collections.emptyList();
            } else {
                entries = Collections.unmodifiableList(entries);
            }
            sealed = true;
        }
        return this;
    }

    /**
     * Checks if this filter set is sealed.
     * @return {@code true} if sealed.
     */
    public boolean isSealed() {
        return sealed;
    }

    /**
     * Adds (replaces) all entries from the given set to this one.
     * @param set the set of entries
     * @return {@code this} suitable for chaining.
     */
    @NotNull
    public FilterSet addAll(@NotNull FilterSet<E> set) {
        if (sealed) {
            throw new UnsupportedOperationException("FilterSet is sealed.");
        }
        entries = null;
        if (set.entries != null) {
            entries = new LinkedList<>(set.entries);
        }
        return this;
    }

    /**
     * Adds a new item filter as included entry.
     * @param filter the filter
     * @return {@code this} suitable for chaining.
     */
    @NotNull
    public FilterSet addInclude(@NotNull E filter) {
        addEntry(new Entry<>(filter, true));
        return this;
    }

    /**
     * Adds a new item filter as excluded entry.
     * @param filter the filter
     * @return {@code this} suitable for chaining.
     */
    @NotNull
    public FilterSet addExclude(@NotNull E filter) {
        addEntry(new Entry<>(filter, false));
        return this;
    }

    /**
     * Internally adds a new entry to the list
     * @param e the entry
     */
    private void addEntry(@NotNull Entry<E> e) {
        if (sealed) {
            throw new UnsupportedOperationException("FilterSet is sealed.");
        }
        if (entries == null) {
            entries = new LinkedList<>();
        }
        entries.add(e);
    }

    /**
     * Returns the list of entries
     * @return the list of entries
     */
    @NotNull
    public List<Entry<E>> getEntries() {
        seal();
        //noinspection ConstantConditions
        return entries;
    }

    /**
     * Checks if this filter set has any entries defined.
     * @return {@code true} if empty
     */
    public boolean isEmpty() {
        return entries == null || entries.isEmpty();
    }

    /**
     * Checks if the given item is covered by this filter set. I.e. if the node
     * addressed by the {@code root} path is an ancestor of the given item.
     *
     * @param path path of the item
     * @return {@code true} if this set covers the given item
     */
    public boolean covers(@NotNull String path) {
        return path.equals(root) || path.startsWith(rootPattern);
    }

    /**
     * Checks if the given item is an ancestor of the root node.
     * @param path path of the item to check
     * @return {@code true} if the given item is an ancestor
     */
    public boolean isAncestor(@NotNull String path) {
        return path.equals(root) || root.startsWith(path + "/") || "/".equals(path);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dump(@NotNull DumpContext ctx, boolean isLast) {
        ctx.printf(false, "root: %s, mode %s", getRoot(), getImportMode());
        if (entries != null) {
            Iterator<Entry<E>> iter = entries.iterator();
            while (iter.hasNext()) {
                Entry<E> e = iter.next();
                e.dump(ctx, !iter.hasNext());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int result = root.hashCode();
        result = 31 * result + (entries != null ? entries.hashCode() : 0);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FilterSet)) return false;

        FilterSet<E> filterSet = (FilterSet<E>) o;
        if (entries != null ? !entries.equals(filterSet.entries) : filterSet.entries != null) return false;
        return root.equals(filterSet.root);

    }

    @Override
    public String toString() {
        StringWriter stringWriter = new StringWriter();
        dump(new DumpContext(new PrintWriter(stringWriter)), true);
        return stringWriter.toString();
    }


    /**
     * Holds a filter entry
     */
    public static class Entry<E extends Filter> implements Dumpable {

        /**
         * The item filter
         */
        @NotNull
        protected final E filter;

        /**
         * indicates if this an include filter
         */
        protected final boolean include;

        /**
         * Constructs a new entry
         * @param filter the filter
         * @param include the include flag
         */
        public Entry(@NotNull E filter, boolean include) {
            this.filter = filter;
            this.include = include;
        }

        /**
         * Returns the filter of this entry
         * @return the filter
         */
        @NotNull
        public E getFilter() {
            return filter;
        }

        /**
         * Returns the 'include' flag of this entry
         * @return the flag
         */
        public boolean isInclude() {
            return include;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void dump(@NotNull DumpContext ctx, boolean isLast) {
            if (include) {
                ctx.println(isLast, "include");
            } else {
                ctx.println(isLast, "exclude");
            }
            ctx.indent(isLast);
            filter.dump(ctx, true);
            ctx.outdent();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            int result = filter.hashCode();
            result = 31 * result + (include ? 1 : 0);
            return result;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Entry)) return false;
            Entry entry = (Entry) o;
            return include == entry.include && filter.equals(entry.filter);
        }

        @Override
        public String toString() {
            StringWriter stringWriter = new StringWriter();
            dump(new DumpContext(new PrintWriter(stringWriter)), true);
            return stringWriter.toString();
        }

        
    }
}
