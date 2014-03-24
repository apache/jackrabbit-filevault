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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.ListIterator;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>SiblingNames</code>...
 */
public class NodeNameList {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(NodeNameList.class);

    private final LinkedHashSet<String> names = new LinkedHashSet<String>();

    public void addName(String name) {
        names.add(name);
    }

    public boolean contains(String name) {
        return names.contains(name);
    }

    public LinkedHashSet<String> getNames() {
        return names;
    }

    public boolean needsReorder(Node parent) throws RepositoryException {
        // could perform more comprehensive check
        return names.size() > 1 && parent.getPrimaryNodeType().hasOrderableChildNodes();
    }

    public boolean isEmpty() {
        return names.isEmpty();
    }

    public boolean restoreOrder(Node parent) throws RepositoryException {
        // assume needsReorder check is performed
        // quick check if node is checked out
        if (!parent.isCheckedOut()) {
            log.warn("Unable to restore order of a checked-in node: " + parent.getPath());
            return false;
        }
        int size = names.size();
        String last = null;
        ArrayList<String> list = new ArrayList<String>(names);
        ListIterator<String> iter = list.listIterator(size);
        while (iter.hasPrevious()) {
            String prev = iter.previous();
            if (parent.hasNode(prev)) {
                log.debug("ordering {} before {}", prev, last);
                try {
                    parent.orderBefore(prev, last);
                } catch (Exception e) {
                    // probably an error in jcr2spi
                    String path = parent.getPath() + "/" + prev;
                    log.warn("Ignoring unexpected error during reorder of {}: {}", path, e.toString());
                }
                last = prev;
            }
        }
        return true;
    }

}