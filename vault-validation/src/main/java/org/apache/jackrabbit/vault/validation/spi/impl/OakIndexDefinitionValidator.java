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

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.jackrabbit.oak.plugins.index.IndexConstants;
import org.apache.jackrabbit.oak.spi.security.authorization.accesscontrol.AccessControlConstants;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.FilterValidator;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Validates that packages not having the property {@code allowIndexDefinitions=true} must not contain index definitions.
 *  NPR-14102 - Automated check for index definition 
 */
public final class OakIndexDefinitionValidator implements FilterValidator, DocumentViewXmlValidator {

    static final String MESSAGE_POTENTIAL_INDEX_IN_FILTER = "Package '%s' contains filter rule overwriting a potential index definition below '%s' but the according property " + PackageProperties.NAME_ALLOW_INDEX_DEFINITIONS + " is not set to 'true'";
    static final String MESSAGE_INDEX_AT_NODE = "Package '%s' contains index definition but the according property " + PackageProperties.NAME_ALLOW_INDEX_DEFINITIONS + " is not set to 'true'";
    
    private final Path packageRootPathOfNotAllowedIndexDefinition;
    private final ValidationMessageSeverity defaultMessageSeverity;

    public OakIndexDefinitionValidator(Path path, ValidationMessageSeverity defaultMessageSeverity) {
        this.packageRootPathOfNotAllowedIndexDefinition = path;
        this.defaultMessageSeverity = defaultMessageSeverity;
    }
    @Override
    public @Nullable Collection<ValidationMessage> validate(@NotNull WorkspaceFilter filter) {
        Collection<ValidationMessage> violations = new LinkedList<>();
        violations.addAll(collectIndexPaths(filter.getFilterSets()));
        return violations;
    }

    public Collection<ValidationMessage> collectIndexPaths(List<PathFilterSet> pathFilters) {
        Collection<ValidationMessage> violations = new LinkedList<>();
        for (PathFilterSet pathFilter : pathFilters) {
            // support other index roots (https://jackrabbit.apache.org/oak/docs/query/indexing.html#Index_Definition_Location)
            if (pathFilter.isAncestor("/" + IndexConstants.INDEX_DEFINITIONS_NAME) || pathFilter.getRoot().contains("/" + IndexConstants.INDEX_DEFINITIONS_NAME +"/") || pathFilter.getRoot().endsWith("/" + IndexConstants.INDEX_DEFINITIONS_NAME)) {
                // exclude ACL only entries (only a heuristic because the name might differ)
                if (pathFilter.getRoot().contains("/" + AccessControlConstants.REP_POLICY)) {
                    violations.add(new ValidationMessage(ValidationMessageSeverity.DEBUG, "Ignoring filter entry " + pathFilter  + " as it is referring to an ACL"));
                } else {
                    violations.add(new ValidationMessage(defaultMessageSeverity, String.format(MESSAGE_POTENTIAL_INDEX_IN_FILTER, packageRootPathOfNotAllowedIndexDefinition, pathFilter.getRoot())));
                }
            }
        }
        return violations;
    }

    @Override
    public @Nullable Collection<ValidationMessage> validate(@NotNull DocViewNode node, @NotNull NodeContext nodeContext, boolean isRoot) {
        ValidationMessage violation = null;
        if (IndexConstants.INDEX_DEFINITIONS_NODE_TYPE.equals(node.primary)) {
            violation = new ValidationMessage(defaultMessageSeverity, String.format(MESSAGE_INDEX_AT_NODE, packageRootPathOfNotAllowedIndexDefinition));
        }
        return violation != null ? Collections.singleton(violation) : null;
    }

    @Override
    public @Nullable Collection<ValidationMessage> done() {
        return null;
    }

}
