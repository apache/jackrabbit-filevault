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
import java.util.HashMap;
import java.util.Map;

import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *  Check for duplicate jcr:uuid values
 */
public class DuplicateUuidValidator implements DocumentViewXmlValidator {

    protected static final String MESSAGE_DUPLICATE_UUID = "Found the same jcr:uuid value '%s' in '%s' and '%s'";
    private final ValidationMessageSeverity severity;
    private final WorkspaceFilter filter;
    
    
    private Map<String, String> uuidsAndPaths;
    
    public DuplicateUuidValidator(ValidationMessageSeverity severity, WorkspaceFilter filter) {
        this.severity = severity;
        this.filter = filter;
        uuidsAndPaths = new HashMap<>();
    }

    @Override
    public Collection<ValidationMessage> validate(@NotNull DocViewNode node, @NotNull NodeContext nodeContext, boolean isRoot) {
        if (node.uuid != null && filter.contains(nodeContext.getNodePath())) {
            String duplicateUuidPath = uuidsAndPaths.put(node.uuid, nodeContext.getNodePath());
            if (duplicateUuidPath != null) {
                return Collections.singleton(new ValidationMessage(severity, String.format(MESSAGE_DUPLICATE_UUID, node.uuid, duplicateUuidPath, nodeContext.getNodePath())));
            }
        }
        return null;
    }

    @Override
    public @Nullable Collection<ValidationMessage> done() {
        return null;
    }

}
