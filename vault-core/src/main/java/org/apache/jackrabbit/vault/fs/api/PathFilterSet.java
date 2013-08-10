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

import java.util.List;

/**
 * The path filter set holds a set of path filters each attributes as include
 * or exclude filter. The evaluation of the set allows included paths and
 * rejects excluded paths.
 * <p/>
 * Additionally it contains a "root" path for which the filters are evaluated.
 * if an item has not the node addressed by the root path as ancestor, it is
 * always excluded.
 *
 */
public class PathFilterSet extends FilterSet<PathFilter> {

    /**
     * The include all item filter set
     */
    public static final PathFilterSet INCLUDE_ALL =
            (PathFilterSet) new PathFilterSet().addInclude(PathFilter.ALL).seal();

    /**
     * The exclude all item filter set
     */
    public static final PathFilterSet EXCLUDE_ALL =
            (PathFilterSet) new PathFilterSet().addExclude(PathFilter.ALL).seal();


    /**
     * specifies if only relative patters are included in this filter ser
     */
    private boolean onlyRelativePatterns;

    /**
     * Default constructor. initializes the root path to "/"
     */
    public PathFilterSet() {
        super();
    }

    /**
     * Creates a new path filter set and sets the respective root path
     * @param root path
     */
    public PathFilterSet(String root) {
        super(root);
    }

    /**
     * Evaluates the filters if this set does {@link #covers(String) cover} the
     * given item. otherwise <code>false</code> is returned.
     * The result of the evaluation is the polarity of the last matched path.
     * If no filter matches it returns <code>true</code>
     * if the first filter is an exclude filter or if no filter is defined;
     * <code>false</code> if the first filter is an include filter.
     *
     * @param path the path to check
     * @return <code>true</code> if this set matches the item
     */
    public boolean contains(String path) {
        if (!covers(path)) {
            return false;
        }
        List<Entry<PathFilter>> entries = getEntries();
        if (entries.isEmpty()) {
            return true;
        } else {
            boolean result = !entries.get(0).include;
            for (Entry<PathFilter> entry: entries) {
                if (entry.filter.matches(path)) {
                    result = entry.include;
                }
            }
            return result;
        }
    }

    @Override
    public FilterSet seal() {
        if (!isSealed()) {
            super.seal();
            onlyRelativePatterns = true;
            for (Entry<PathFilter> entry: getEntries()) {
                if (!entry.include || entry.filter.isAbsolute()) {
                    onlyRelativePatterns = false;
                    break;
                }
            }
        }
        return this;
    }


    /**
     * Translates this path filter with the given mapping. Note that only absolute filters can be translated.
     * @param mapping the mapping to apply
     * @return the new filter
     * @since 2.4.10
     */
    public PathFilterSet translate(PathMapping mapping) {
        if (mapping == null) {
            return this;
        }
        PathFilterSet mapped = new PathFilterSet(mapping.map(getRoot()));
        mapped.setImportMode(getImportMode());
        for (Entry<PathFilter> e: getEntries()) {
            if (e.isInclude()) {
                mapped.addInclude(e.getFilter().translate(mapping));
            } else {
                mapped.addExclude(e.getFilter().translate(mapping));
            }
        }
        mapped.seal();
        return mapped;
    }

    /**
     * Checks if this path filter set only contains entries that are relative
     * include patterns, eg: ".* /foo.*". in this case the aggregator will use a
     * different strategy when providing non matching leave nodes.
     * @return <code>true</code> if only contains relative patterns
     */
    public boolean hasOnlyRelativePatterns() {
        seal();
        return onlyRelativePatterns;
    }
}