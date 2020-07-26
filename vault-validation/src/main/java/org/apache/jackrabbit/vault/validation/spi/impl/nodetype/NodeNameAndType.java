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
    private final Name name;
    private final EffectiveNodeType effectiveNodeType;
    private final @NotNull List<NodeNameAndType> children;
    private final @Nullable NodeNameAndType parent;

    public static NodeNameAndType createUnknownNodeNameAndType(@Nullable NodeNameAndType parent) {
        return new NodeNameAndType(parent);
    }

    private NodeNameAndType(@Nullable NodeNameAndType parent) {
        this.parent = parent;
        if (parent != null) {
            parent.addChild(this);
        }
        this.effectiveNodeType = null;
        children = new LinkedList<>();
        this.name = null;
    }
    
    @SuppressWarnings("null")
    public NodeNameAndType(@Nullable NodeNameAndType parent, @NotNull NameResolver nameResolver, @NotNull EffectiveNodeTypeProvider effectiveNodeTypeProvider, @NotNull DocViewNode node) throws IllegalNameException, NamespaceException, ConstraintViolationException, NoSuchNodeTypeException {
        try {
            this.name = nameResolver.getQName(node.name);
        } catch (IllegalNameException|NamespaceException e) {
            throw new IllegalNameException("Invalid node name " + node.name + ": '" + e.getMessage() + "'", e);
        }
        Collection<Name> types = new LinkedList<>();
        try {
            types.add(nameResolver.getQName(node.primary));
        } catch (IllegalNameException|NamespaceException e) {
            throw new IllegalNameException("Invalid primary type " + node.primary + ": '" + e.getMessage() + "'", e);
        }
        if (node.mixins != null) {
            for (String mixin : node.mixins) {
                try {
                    types.add(nameResolver.getQName(mixin));
                } catch (IllegalNameException|NamespaceException e) { 
                    throw new IllegalNameException("Invalid mixin type " + mixin + ": '" + e.getMessage() + "'", e);
                }
            }
        }
        effectiveNodeType = effectiveNodeTypeProvider.getEffectiveNodeType(types.toArray(new Name[0]));
        children = new LinkedList<>();
        if (parent != null) {
            parent.addChild(this);
        }
        this.parent = parent;
    }

    public boolean isUnknown() {
        return this.effectiveNodeType == null && this.name == null;
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

    @Override
    public String toString() {
        return "NodeNameAndType [" + (name != null ? "name=" + name + ", " : "")
                + (effectiveNodeType != null ? "effectiveNodeType=" + effectiveNodeType + ", " : "")
                + (children != null ? "children=" + children + ", " : "") + (parent != null ? "parent=" + parent : "") + "]";
    }
}