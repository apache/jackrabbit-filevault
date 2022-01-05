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
import java.util.Collections;
import java.util.LinkedList;

import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.jetbrains.annotations.NotNull;

/**
 * @see <a href="https://issues.apache.org/jira/browse/JCRVLT-255">JCRVLT-255</a>
 */
public class MergeLimitationsValidator implements DocumentViewXmlValidator {

    protected static final String PACKAGE_NON_ROOT_NODE_MERGED = "Non-empty Node '%s' is supposed to be imported with mode 'merge' but it is not the aggregator's root node. This is currently not supported by FileVault (https://issues.apache.org/jira/browse/JCRVLT-255).";
    private final ValidationMessageSeverity severity;
    private final Collection<String> rootNodePathsOfMergeRules;

    public MergeLimitationsValidator(ValidationMessageSeverity severity, WorkspaceFilter filter) {
        super();
        this.severity = severity;
        this.rootNodePathsOfMergeRules = new LinkedList<>();

        // go through all filters,
        for (PathFilterSet pathFilterSet : filter.getFilterSets()) {
            // find those with mode=merge
            if (pathFilterSet.getImportMode() == ImportMode.MERGE) {
                rootNodePathsOfMergeRules.add(pathFilterSet.getRoot());
            }
        }
    }

    @Override
    public Collection<ValidationMessage> done() {
        return null;
    }

    @Override
    public Collection<ValidationMessage> validate(@NotNull DocViewNode node, @NotNull NodeContext nodeContext, boolean isRoot) {
        // find out if one of the filter roots is pointing to any of the aggregator's non-root nodes
        if (!isRoot && !node.props.isEmpty() && rootNodePathsOfMergeRules.contains(nodeContext.getNodePath())) {
            return Collections.singleton(new ValidationMessage(severity, String.format(PACKAGE_NON_ROOT_NODE_MERGED, nodeContext.getNodePath())));
        }
        return null;
    }
}
