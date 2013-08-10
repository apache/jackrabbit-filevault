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

package org.apache.jackrabbit.vault.util;

import java.util.Comparator;

import javax.jcr.Item;
import javax.jcr.RepositoryException;

/**
 * <code>ItemNameComparator</code>...
 */
public class ItemNameComparator implements Comparator<Item> {

    public static final ItemNameComparator INSTANCE = new ItemNameComparator();

    public int compare(Item o1, Item o2) {
        try {
            // sort namespaced first
            String n1 = o1.getName().toLowerCase();
            String n2 = o2.getName().toLowerCase();
            int i1 = n1.indexOf(':');
            int i2 = n2.indexOf(':');
            if (i1 >=0 && i2 < 0) {
                return -1;
            } else if (i1 < 0 && i2 >=0) {
                return 1;
            } else {
                return n1.compareTo(n2);
            }
        } catch (RepositoryException e) {
            throw new IllegalStateException(e);
        }
    }
}