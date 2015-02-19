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

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * The item filter set holds a set of item filters each attributed as include
 * or exclude filter. The evaluation of the set allows included items and
 * rejects excluded items.
 * <p/>
 * Additionally it contains a "root" path for which the filters are evaluated.
 * if an item has not the node addressed by the root path as ancestor, it is
 * always excluded.
 */
public abstract class FilterSet<E extends Filter> implements Dumpable {

    /**
     * root path of this definition
     */
    private String root;

    /**
     * root patten to check for inclusion
     */
    private String rootPattern;

    /**
     * filter entries
     */
    private List<Entry<E>> entries;

    /**
     * flag that indicates if set is sealed
     */
    private boolean sealed;

    /**
     * import mode. defaults to {@link ImportMode#REPLACE}.
     */
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
    public String getRoot() {
        return root.equals("") ? "/" : root;
    }

    /**
     * Sets the root path
     * @param path root path
     */
    public void setRoot(String path) {
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
    public ImportMode getImportMode() {
        return mode;
    }

    /**
     * Sets the import mode.
     * @param mode import mode
     */
    public void setImportMode(ImportMode mode) {
        if (sealed) {
            throw new UnsupportedOperationException("FilterSet is sealed.");
        }
        this.mode = mode;
    }

    /**
     * Seals this list, i.e. makes it unmodifiable.
     * @return this list
     */
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
     * @return <code>true</code> if sealed.
     */
    public boolean isSealed() {
        return sealed;
    }

    /**
     * Adds (replaces) all entries from the given set to this one.
     * @param set the set of entries
     * @return <code>this</code> suitable for chaining.
     */
    public FilterSet addAll(FilterSet<E> set) {
        if (sealed) {
            throw new UnsupportedOperationException("FilterSet is sealed.");
        }
        if (entries == null) {
            entries = new LinkedList<Entry<E>>(set.entries);
        } else {
            entries.clear();
            entries.addAll(set.entries);
        }
        return this;
    }

    /**
     * Adds a new item filter as included entry.
     * @param filter the filter
     * @return <code>this</code> suitable for chaining.
     */
    public FilterSet addInclude(E filter) {
        addEntry(new Entry<E>(filter, true));
        return this;
    }

    /**
     * Adds a new item filter as excluded entry.
     * @param filter the filter
     * @return <code>this</code> suitable for chaining.
     */
    public FilterSet addExclude(E filter) {
        addEntry(new Entry<E>(filter, false));
        return this;
    }

    /**
     * Internally adds a new entry to the list
     * @param e the entry
     */
    private void addEntry(Entry<E> e) {
        if (sealed) {
            throw new UnsupportedOperationException("FilterSet is sealed.");
        }
        if (entries == null) {
            entries = new LinkedList<Entry<E>>();
        }
        entries.add(e);
    }

    /**
     * Returns the list of entries
     * @return the list of entries
     */
    public List<Entry<E>> getEntries() {
        seal();
        return entries;
    }

    /**
     * Checks if this filter set has any entries defined.
     * @return <code>true</code> if empty
     */
    public boolean isEmpty() {
        return entries == null || entries.isEmpty();
    }

    /**
     * Checks if the given item is covered by this filter set. I.e. if the node
     * addressed by the <code>root</code> path is an ancestor of the given item.
     *
     * @param path path of the item
     * @return <code>true</code> if this set covers the given item
     */
    public boolean covers(String path) {
        return path.equals(root) || path.startsWith(rootPattern);
    }

    /**
     * Checks if the given item is an ancestor of the root node.
     * @param path path of the item to check
     * @return <code>true</code> if the given item is an ancestor
     */
    public boolean isAncestor(String path) {
        return path.equals(root) || root.startsWith(path + "/") || path.equals("/");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dump(DumpContext ctx, boolean isLast) {
        ctx.printf(false, "root: %s", getRoot());
        if (entries != null) {
            Iterator<Entry<E>> iter = entries.iterator();
            while (iter.hasNext()) {
                Entry e = iter.next();
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

        FilterSet filterSet = (FilterSet) o;
        if (entries != null ? !entries.equals(filterSet.entries) : filterSet.entries != null) return false;
        return root.equals(filterSet.root);

    }

    /**
     * Holds a filter entry
     */
    public static class Entry<E extends Filter> implements Dumpable {

        /**
         * The item filter
         */
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
        public Entry(E filter, boolean include) {
            this.filter = filter;
            this.include = include;
        }

        /**
         * Returns the filter of this entry
         * @return the filter
         */
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
        public void dump(DumpContext ctx, boolean isLast) {
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

    }
}