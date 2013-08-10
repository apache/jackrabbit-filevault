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

/**
 * <code>PathComparator</code>...
 *
 */
public class PathComparator implements Comparator<String> {

    private final char separator;

    private boolean reverse;

    public PathComparator() {
        separator = '/';
    }

    public PathComparator(boolean reverse) {
        separator = '/';
        this.reverse = reverse;
    }

    public PathComparator(char separator) {
        this.separator = separator;
    }

    public PathComparator(char separator, boolean reverse) {
        this.separator = separator;
        this.reverse = reverse;
    }

    /**
     * {@inheritDoc}
     *
     * Compared to the {@link String#compareTo(String)} it handles the '/'
     * differently giving it the highest priority so that:
     *
     * "/a" < "/b"
     * "/a1foo" < "/a/foo"
     */
    public int compare(String o1, String o2) {
        if (o1.equals(o2)) {
            return 0;
        }
        int last0 = 0;
        int last1 = 0;
        int i0 = o1.indexOf(separator, 1);
        int i1 = o2.indexOf(separator, 1);
        while (i0 > 0 && i1 > 0) {
            int c = o1.substring(last0, i0).compareTo(o2.substring(last1, i1));
            if (c != 0) {
                return reverse ? -c : c;
            }
            last0 = i0;
            last1 = i1;
            i0 = o1.indexOf(separator, i0 + 1);
            i1 = o2.indexOf(separator, i1 + 1);
        }
        int ret;
        if (i0 > 0) {
            ret = 1;
        } else if (i1 > 0) {
            ret = -1;
        } else {
            // compare last segment
            ret = o1.substring(last0).compareTo(o2.substring(last1));
        }
        return reverse ? -ret : ret;
    }
}