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

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.validation.ValidationExecutor;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.GenericJcrDataValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;

/**
 *  Check for empty elements (used for ordering purposes)
 *  which are included in the filter with import=replace as those are actually not replaced!
 *  @see <a href="https://issues.apache.org/jira/browse/JCRVLT-251">JCRVLT-251</a>
 */
public class EmptyElementsValidator implements DocumentViewXmlValidator, GenericJcrDataValidator {

    protected static final String MESSAGE_EMPTY_NODES = "Found empty nodes: %s (used for ordering only) which are included in the filter with mode=merge. Rather use the according include/exclude patterns.";
    private final ValidationMessageSeverity severity;
    private final Map<String, Path> emptyNodePathsAndFiles;
    private final Collection<String> nonEmptyNodePaths;
    private final WorkspaceFilter filter;
    
    private Collection<String> affectedFilterRoots;
    
    
    public EmptyElementsValidator(ValidationMessageSeverity severity, WorkspaceFilter filter) {
        this.severity = severity;
        this.emptyNodePathsAndFiles = new LinkedHashMap<>();
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
        emptyNodePathsAndFiles.keySet().removeAll(nonEmptyNodePaths);
        if (!emptyNodePathsAndFiles.isEmpty()) {
            String nodes = emptyNodePathsAndFiles.entrySet()
                    .stream()
                    .map(e -> "'" + e.getKey() + "' (in '" + e.getValue() + "')")
                    .collect(Collectors.joining(", "));
            return Collections.singleton(new ValidationMessage(severity, String.format(MESSAGE_EMPTY_NODES, nodes)));
        }
        return null;
    }

    @Override
    public Collection<ValidationMessage> validate(DocViewNode node, String nodePath, Path filePath, boolean isRoot) {
        if (isBelowAffectedFilterRoots(nodePath)) {
            if (node.primary == null && node.mixins == null && node.props.isEmpty() && filter.contains(nodePath) && filter.getImportMode(nodePath) == ImportMode.REPLACE) {
                // only relevant if no other merge mode
                emptyNodePathsAndFiles.put(nodePath, filePath);
            } else {
                nonEmptyNodePaths.add(nodePath);
            }
        }
        return null;
    }

    @Override
    public Collection<ValidationMessage> validateJcrData(InputStream input, Path filePath, Map<String, Integer> nodePathsAndLineNumbers) {
        // never validate actual input
        // this should never be called
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
    public boolean shouldValidateJcrData(Path filePath) {
        String nodePath = ValidationExecutor.filePathToNodePath(filePath);
        if (isBelowAffectedFilterRoots(nodePath)) {
            nonEmptyNodePaths.add(nodePath);
        }
        return false;
    }

}
