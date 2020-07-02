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
package org.apache.jackrabbit.vault.validation.spi.impl.nodetype;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.jcr.NamespaceException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;

import org.apache.jackrabbit.jcr2spi.nodetype.EffectiveNodeType;
import org.apache.jackrabbit.jcr2spi.nodetype.EffectiveNodeTypeProvider;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.QNodeDefinition;
import org.apache.jackrabbit.spi.commons.conversion.IllegalNameException;
import org.apache.jackrabbit.spi.commons.conversion.NameResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 
 * Encapsulates the expanded name and its underlying effective node type.
 * In addition stores references to the child node names and types.
 */
final class NodeNameAndType {
    private final @NotNull Name name;
    private final @NotNull EffectiveNodeType effectiveNodeType;
    private final @NotNull List<NodeNameAndType> children;
    private final @Nullable NodeNameAndType parent;

    @SuppressWarnings("null")
    public NodeNameAndType(@Nullable NodeNameAndType parent, @NotNull NameResolver nameResolver, @NotNull EffectiveNodeTypeProvider effectiveNodeTypeProvider, @NotNull DocViewNode node) throws IllegalNameException, NamespaceException, ConstraintViolationException, NoSuchNodeTypeException {
        this.name = nameResolver.getQName(node.name);
        Collection<Name> types = new LinkedList<>();
        types.add(nameResolver.getQName(node.primary));
        if (node.mixins != null) {
            for (String mixin : node.mixins) {
                types.add(nameResolver.getQName(mixin));
            }
        }
        effectiveNodeType = effectiveNodeTypeProvider.getEffectiveNodeType(types.toArray(new Name[0]));
        children = new LinkedList<>();
        if (parent != null) {
            parent.addChild(this);
        }
        this.parent = parent;
    }

    public boolean fulfillsNodeDefinition(QNodeDefinition nodeDefinition) {
        // name must match
        if (!nodeDefinition.getName().equals(NameConstants.ANY_NAME) && !nodeDefinition.getName().equals(name)) {
            return false;
        }

        for (Name requiredType : nodeDefinition.getRequiredPrimaryTypes()) {
            // type must match one of the given types
            if (!effectiveNodeType.includesNodeType(requiredType)) {
                return false;
            }
        }
        return true;
    }

    void addChild(NodeNameAndType nodeNameAndTypes) {
        children.add(nodeNameAndTypes);
    }

    public List<NodeNameAndType> getChildren() {
        return children;
    }

    public Name getName() {
        return name;
    }

    public EffectiveNodeType getEffectiveNodeType() {
        return effectiveNodeType;
    }

    public NodeNameAndType getParent() {
        return parent;
    }
}