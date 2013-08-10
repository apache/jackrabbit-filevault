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

import javax.jcr.Item;
import javax.jcr.RepositoryException;

/**
 * The item filter set holds a set of item filters each attributes as include
 * or exclude filter. The evaluation of the set allows included items and
 * rejects excluded items.
 * <p/>
 * Additionally it contains a "root" path for which the filters are evaluated.
 * if an item has not the node addressed by the root path as ancestor, it is
 * always excluded.
 *
 */
public class ItemFilterSet extends FilterSet<ItemFilter> {

    /**
     * The include all item filter set
     */
    public static final ItemFilterSet INCLUDE_ALL =
            (ItemFilterSet) new ItemFilterSet().addInclude(ItemFilter.ALL).seal();

    /**
     * The exclude all item filter set
     */
    public static final ItemFilterSet EXCLUDE_ALL =
            (ItemFilterSet) new ItemFilterSet().addExclude(ItemFilter.ALL).seal();


    /**
     * Default constructor. initializes the root path to "/"
     */
    public ItemFilterSet() {
        super();
    }

    /**
     * Creates a new item filter set and sets the respective root path
     * @param root path
     */
    public ItemFilterSet(String root) {
        super(root);
    }

    /**
     * Evaluates the filters if this set does {@link #covers(String) cover} the
     * given item. otherwise <code>false</code> is returned.
     * The result of the evaluation is the polarity of the last matched item.
     * If no filter matches it returns <code>true</code>
     * if the first filter is an exclude filter or if no filter is defined;
     * <code>false</code> if the first filter is an include filter.
     *
     * @param item the item to check
     * @param depth the depth to check
     * @return <code>true</code> if this set matches the item
     * @throws RepositoryException if an error occurs.
     */
    public boolean contains(Item item, int depth) throws RepositoryException {
        return contains(item, null, depth);
    }
    /**
     * Evaluates the filters if this set does {@link #covers(String) cover} the
     * given item. otherwise <code>false</code> is returned.
     * The result of the evaluation is the polarity of the last matched item.
     * If no filter matches it returns <code>true</code>
     * if the first filter is an exclude filter or if no filter is defined;
     * <code>false</code> if the first filter is an include filter.
     *
     * @param item the item to check
     * @param path of the item or <code>null</code>
     * @param depth the depth to check
     * @return <code>true</code> if this set matches the item
     * @throws RepositoryException if an error occurs.
     */
    public boolean contains(Item item, String path, int depth) throws RepositoryException {
        if (path == null) {
            path = item.getPath();
        }
        if (!covers(path)) {
            return false;
        }
        List<Entry<ItemFilter>> entries = getEntries();
        if (entries.isEmpty()) {
            return true;
        } else {
            boolean result = !entries.get(0).include;
            for (Entry<ItemFilter> entry: entries) {
                if (entry.filter.matches(item, depth)) {
                    result = entry.include;
                }
            }
            return result;
        }
    }

}