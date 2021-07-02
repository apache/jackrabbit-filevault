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

import static javax.jcr.PropertyType.UNDEFINED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.ItemDefinition;
import javax.jcr.nodetype.NodeDefinition;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.PropertyDefinition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Effective node type as defined by <a href="https://docs.adobe.com/content/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.7.6.5%20Effective%20Node%20Type">JCR 2.0, Chapter 3.7.6.5</a>.
 * The order is an implementation detail (compare with <a href="https://docs.adobe.com/content/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.7.7%20Applicable%20Item%20Definition">JCR 2.0, Chapter 3.7.7</a>)
 * but this implementation replicates the logic from Oak:
 * <ul>
 * <li>local before inherited types</li>
 * <li>named primary types (even inherited ones) before named mixin types</li>
 * <li>residual primary types (even inherited ones) before named mixin types</li>
 * <li>all named item definitions should be considered first (of both primary and mixins) and only afterwards the unnamed ones.</li>
 * <li>the first potential match wins (even if it is only for the undefined type and more type-specific definitions follow later)</li>
 * </ul>
 */
public final class EffectiveNodeType {

    public static EffectiveNodeType ofNode(Node node) throws RepositoryException {
        return ofPrimaryTypeAndMixins(node.getPrimaryNodeType(), node.getMixinNodeTypes());
    }

    public static EffectiveNodeType ofPrimaryTypeAndMixins(NodeType primaryType, NodeType... mixinTypes) {
        List<NodeType> types = new ArrayList<>();
        types.add(primaryType);
        Arrays.stream(mixinTypes).forEach(types::add);
        return new EffectiveNodeType(types);
    }

    private final List<NodeType> nodeTypes;

    private EffectiveNodeType(List<NodeType> nodeTypes) {
        this.nodeTypes = nodeTypes;
    }

    /**
     * The applicable property definition for the given name and type
     * @param name the property name
     * @param isMultiple if this is a multi-value type
     * @param type the property value type
     * @return the applicable property definition
     * 
     * This replicates the logic from https://github.com/apache/jackrabbit-oak/blob/274f92402a12978040939965e92ee4519f2ce1c3/oak-core/src/main/java/org/apache/jackrabbit/oak/plugins/nodetype/EffectiveNodeTypeImpl.java#L365
     */
    public @Nullable PropertyDefinition getApplicablePropertyDefinition(@NotNull String name, boolean isMultiple, int type) {
        return getApplicablePropertyDefinition(pd -> isMultiple == pd.isMultiple() && (type == pd.getRequiredType() || UNDEFINED == type || UNDEFINED == pd.getRequiredType()), name);
    }

    public @Nullable PropertyDefinition getApplicablePropertyDefinition(Predicate<PropertyDefinition> predicate, @NotNull String name) {
        List<PropertyDefinition> propertyDefinitions = nodeTypes.stream().flatMap(nt -> Arrays.stream(nt.getPropertyDefinitions())).collect(Collectors.toList());
        // first named then unnamed
        PropertyDefinition namedPropertyDef = EffectiveNodeType.<PropertyDefinition>getApplicableItemDefinition(propertyDefinitions, predicate, name);
        if (namedPropertyDef == null) {
            // then unnamed
            return EffectiveNodeType.<PropertyDefinition>getApplicableItemDefinition(propertyDefinitions, predicate, null);
        } else {
            return namedPropertyDef;
        }
    }

    /**
     * The applicable node definition for the given name and types
     * @param name the child node name
     * @param types the node types
     * @return the applicable child node definition
     * 
     * This replicates the logic from https://github.com/apache/jackrabbit-oak/blob/274f92402a12978040939965e92ee4519f2ce1c3/oak-core/src/main/java/org/apache/jackrabbit/oak/plugins/nodetype/EffectiveNodeTypeImpl.java#L440
     */
    public @Nullable NodeDefinition getApplicableChildNodeDefinition(@NotNull String name, @NotNull NodeType... types) {
        return getApplicableChildNodeDefinition(nd -> Arrays.stream(nd.getRequiredPrimaryTypeNames()).allMatch(requiredPrimaryType -> Arrays.stream(types).anyMatch(providedType -> providedType.isNodeType(requiredPrimaryType))), name);
    }

    public @Nullable NodeDefinition getApplicableChildNodeDefinition(Predicate<NodeDefinition> predicate, @NotNull String name) {
        List<NodeDefinition> nodeDefinitions = nodeTypes.stream().flatMap(nt -> Arrays.stream(nt.getChildNodeDefinitions())).collect(Collectors.toList());
        // first named then unnamed
        NodeDefinition namedNodeDef = EffectiveNodeType.<NodeDefinition>getApplicableItemDefinition(nodeDefinitions, predicate, name);
        if (namedNodeDef == null) {
            // then unnamed
            return EffectiveNodeType.<NodeDefinition>getApplicableItemDefinition(nodeDefinitions, predicate, null);
        } else {
            return namedNodeDef;
        }
    }

    private static @Nullable <T extends ItemDefinition> T getApplicableItemDefinition(List<T> itemDefinitions, Predicate<T> predicate, @Nullable String name) {
        final Predicate<ItemDefinition> namePredicate;
        if (name != null) {
            namePredicate = pd -> name.equals(pd.getName());
        } else {
            namePredicate = pd -> "*".equals(pd.getName());
        }
        // either named or residual child node definitions
        Optional<T> childNodeDef = itemDefinitions.stream().filter(predicate).filter(namePredicate).findFirst();
        if (childNodeDef.isPresent()) {
            return childNodeDef.get();
        }
        return null;
    }
}