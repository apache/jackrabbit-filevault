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
package org.apache.jackrabbit.vault.validation.spi.impl;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.NodePathValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *  Check for empty elements (used for ordering purposes)
 *  which are included in the filter with import=replace as those are actually not replaced!
 *  @see <a href="https://issues.apache.org/jira/browse/JCRVLT-251">JCRVLT-251</a>
 */
public class EmptyElementsValidator implements DocumentViewXmlValidator, NodePathValidator {

    protected static final String MESSAGE_EMPTY_NODES = "Found empty node (used for ordering only) without an accompanying folder which are included in the filter with mode=replace. Either remove the empty node or add at least the 'jcr:primaryType' attribute to make this node really get replaced.";
    private final ValidationMessageSeverity severity;
    private final List<NodeContext> emptyNodes;
    private final List<String> nonEmptyNodePaths;
    private final WorkspaceFilter filter;
    
    private Collection<String> affectedFilterRoots;
    
    
    public EmptyElementsValidator(ValidationMessageSeverity severity, WorkspaceFilter filter) {
        this.severity = severity;
        this.emptyNodes = new LinkedList<>();
        this.nonEmptyNodePaths = new LinkedList<>();
        this.filter = filter;
        // collect all filter roots with import mode == replace
        affectedFilterRoots = new LinkedList<>();
        for (PathFilterSet set : filter.getPropertyFilterSets()) {
            if (set.getImportMode() == ImportMode.REPLACE) {
                affectedFilterRoots.add(set.getRoot());
            }
        }
    }

    @Override
    public Collection<ValidationMessage> done() {
        return emptyNodes.stream()
            .filter(e -> nonEmptyNodePaths.stream().noneMatch(n -> n.equals(e.getNodePath())))
            .map(e -> new ValidationMessage(severity, MESSAGE_EMPTY_NODES, e.getNodePath(), e.getFilePath(), e.getBasePath(), null))
            .collect(Collectors.toList());
    }

    @Override
    public Collection<ValidationMessage> validate(@NotNull DocViewNode node, @NotNull NodeContext nodeContext, boolean isRoot) {
        if (isBelowAffectedFilterRoots(nodeContext.getNodePath())) {
            if (node.primary == null && node.mixins == null && node.props.isEmpty() && filter.contains(nodeContext.getNodePath()) && filter.getImportMode(nodeContext.getNodePath()) == ImportMode.REPLACE) {
                // only relevant if no other merge mode
                // ignore rep:policy nodes
                if (!node.name.equals("rep:policy")) {
                    emptyNodes.add(nodeContext);
                }
            } else {
                nonEmptyNodePaths.add(nodeContext.getNodePath());
            }
        }
        return null;
    }

    private boolean isBelowAffectedFilterRoots(String nodePath) {
        for (String affectedFilterRoot : affectedFilterRoots) {
            if (nodePath.startsWith(affectedFilterRoot)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @Nullable Collection<ValidationMessage> validate(@NotNull NodeContext nodeContext) {
        if (isBelowAffectedFilterRoots(nodeContext.getNodePath())) {
            nonEmptyNodePaths.add(nodeContext.getNodePath());
        }
        return null;
    }

}
