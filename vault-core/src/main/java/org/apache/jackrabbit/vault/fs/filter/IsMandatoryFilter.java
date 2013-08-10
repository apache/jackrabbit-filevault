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

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.DumpContext;

/**
 * <code>IsNodeFilter</code>...
 *
 */
public class IsMandatoryFilter extends DepthItemFilter {

    private boolean isMandatory = true;

    public IsMandatoryFilter() {
    }

    public IsMandatoryFilter(boolean isMandatory, int minDepth, int maxDepth) {
        super(minDepth, maxDepth);
        this.isMandatory = isMandatory;
    }

    public IsMandatoryFilter(boolean isMandatory) {
        this(isMandatory, 0, Integer.MAX_VALUE);
    }

    public void setCondition(String node) {
        isMandatory = Boolean.valueOf(node);
    }

    public void setIsMandatory(String node) {
        isMandatory = Boolean.valueOf(node);
    }

    public boolean matches(Item item) throws RepositoryException {
        if (item.isNode()) {
            return ((Node) item).getDefinition().isMandatory() == isMandatory;
        } else {
            return ((Property) item).getDefinition().isMandatory() == isMandatory;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        super.dump(ctx, isLast);
        ctx.indent(isLast);
        ctx.printf(true, "isMandatory: %b", isMandatory);
        ctx.outdent();
    }
    
}