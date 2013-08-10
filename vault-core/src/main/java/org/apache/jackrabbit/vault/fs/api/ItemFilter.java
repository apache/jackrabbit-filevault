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

import javax.jcr.Item;
import javax.jcr.RepositoryException;

/**
 * The item filter is used to include or exclude a set of repository items.
 * It is usually part of a {@link ItemFilterSet}.
 *
 */
public interface ItemFilter extends Filter {

    /**
     * The "Catch all" item filter.
     */
    public static final ItemFilter ALL = new ItemFilter() {

        /**
         * Returns always <code>true</code>
         */
        public boolean matches(Item item, int depth) throws RepositoryException {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        public void dump(DumpContext ctx, boolean isLast) {
            ctx.println(isLast, "ALL");
        }
    };

    /**
     * The "Miss all" item filter.
     */
    public static final ItemFilter NONE = new ItemFilter() {

        /**
         * Returns always <code>false</code>
         */
        public boolean matches(Item item, int depth) throws RepositoryException {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        public void dump(DumpContext ctx, boolean isLast) {
            ctx.println(isLast, "NONE");
        }
    };

    /**
     * Checks if the given item matches this filters criteria. The given depth
     * is relative to some invoker related depth and may not reflect the
     * hierarchical depth of the item in the repository. It up to the
     * implementation how to deal with this value.
     * 
     * @param item the item to check
     * @param depth a relative depth.
     * @return <code>true</code> if this filter matches the criteria;
     *         <code>false</code> otherwise.
     * @throws RepositoryException if an error occurs.
     */
    public boolean matches(Item item, int depth) throws RepositoryException;
}