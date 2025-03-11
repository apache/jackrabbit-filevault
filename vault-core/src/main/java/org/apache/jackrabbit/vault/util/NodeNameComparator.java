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
import java.util.Locale;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

/**
 * {@code ItemNameComparator}...
 */
public class NodeNameComparator implements Comparator<Node> {

    public static final NodeNameComparator INSTANCE = new NodeNameComparator();

    public int compare(Node o1, Node o2) {
        try {
            // sort namespaced first
            String n1 = o1.getName().toLowerCase(Locale.ROOT);
            String n2 = o2.getName().toLowerCase(Locale.ROOT);
            int i1 = n1.indexOf(':');
            int i2 = n2.indexOf(':');
            if (i1 >=0 && i2 < 0) {
                return -1;
            } else if (i1 < 0 && i2 >=0) {
                return 1;
            } else if (n1.equals(n2)) {
                // compare indexes for SNS
                return o1.getIndex() - o2.getIndex();
            } else {
                return n1.compareTo(n2);
            }
        } catch (RepositoryException e) {
            throw new IllegalStateException(e);
        }
    }
}