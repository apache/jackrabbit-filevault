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
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.ItemFilter;
import org.apache.jackrabbit.vault.util.JcrConstants;

/**
 * Implements an item filter that matches if a node only has the primary
 * properties: jcr:primaryType, jcr:mixinTypes, jcr:uuid, jcr:created and
 * jcr:createdBy
 */
public class BaseFilter implements ItemFilter {

    private static final Set<String> validNames = new HashSet<String>();
    static {
        validNames.add(JcrConstants.JCR_PRIMARYTYPE);
        validNames.add(JcrConstants.JCR_MIXINTYPES);
        validNames.add(JcrConstants.JCR_UUID);
        validNames.add(JcrConstants.JCR_CREATED);
        validNames.add(JcrConstants.JCR_CREATED_BY);
    }


    /**
     * {@inheritDoc}
     */
    public boolean matches(Item item, int depth) throws RepositoryException {
        if (item.isNode()) {
            PropertyIterator iter = ((Node) item).getProperties();
            while (iter.hasNext()) {
                String name = iter.nextProperty().getName();
                if (!validNames.contains(name)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        ctx.printf(isLast, "%s:", getClass().getSimpleName());
    }
}