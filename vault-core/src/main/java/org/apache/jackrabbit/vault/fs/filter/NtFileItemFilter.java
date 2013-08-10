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

package org.apache.jackrabbit.vault.fs.filter;

import java.util.HashSet;
import java.util.Set;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.ItemFilter;
import org.apache.jackrabbit.vault.util.JcrConstants;

/**
 * The nt file item filter matches all properties that are defined my the
 * nt:file or nt:resource node type. the later only, if the respective nodes
 * name is 'jcr:content'.
 *
 * Additionally the properties 'jcr:encoding' can be configured to be excluded.
 *
 */
public class NtFileItemFilter implements ItemFilter {

    private static final Set<String> fileNames = new HashSet<String>();
    static {
        fileNames.add(JcrConstants.JCR_PRIMARYTYPE);
        fileNames.add(JcrConstants.JCR_MIXINTYPES);
        fileNames.add(JcrConstants.JCR_UUID);
        fileNames.add(JcrConstants.JCR_CREATED);
        fileNames.add(JcrConstants.JCR_CREATED_BY);
    }

    private static final Set<String> resNames = new HashSet<String>();
    static {
        resNames.add(JcrConstants.JCR_ENCODING);
        resNames.add(JcrConstants.JCR_MIMETYPE);
        resNames.add(JcrConstants.JCR_PRIMARYTYPE);
        resNames.add(JcrConstants.JCR_MIXINTYPES);
        resNames.add(JcrConstants.JCR_UUID);
        resNames.add(JcrConstants.JCR_LASTMODIFIED);
        resNames.add(JcrConstants.JCR_DATA);
    }

    /**
     * {@inheritDoc}
     *
     * @return <code>true</code> if the item is a nt:file or nt:resource property
     */
    public boolean matches(Item item, int depth) throws RepositoryException {
        if (item.isNode()) {
            // include nt:file node
            Node node = (Node) item;
            if (depth == 0) {
                return node.isNodeType(JcrConstants.NT_FILE);
            } else if (depth == 1) {
                // include jcr:content
                return item.getName().equals(JcrConstants.JCR_CONTENT);
            } else {
                return false;
            }
        } else {
            if (depth == 1) {
                return fileNames.contains(item.getName());
            } else if (depth == 2 && item.getParent().getName().equals(JcrConstants.JCR_CONTENT)) {
                return resNames.contains(item.getName());
            } else {
                return false;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        ctx.printf(isLast, "%s:", getClass().getSimpleName());
    }
}