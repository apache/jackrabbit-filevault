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

import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;

/**
 * Makes sure that each node in a docview file containing at least one other property defines the primary type
 */
public class PrimaryNodeTypeValidator implements DocumentViewXmlValidator {

    protected static final String MESSAGE_MISSING_PRIMARY_TYPE = "Mandatory jcr:primaryType missing on node '%s'";
    private final ValidationMessageSeverity severity;
    private final WorkspaceFilter filter;

    public PrimaryNodeTypeValidator(ValidationMessageSeverity severity, WorkspaceFilter filter) {
        super();
        this.severity = severity;
        this.filter = filter;
    }

    @Override
    public Collection<ValidationMessage> done() {
        return null;
    }

    @Override
    public Collection<ValidationMessage> validate(DocViewNode node, String nodePath, Path filePath, boolean isRoot) {
        if (node.primary == null) {
            // only an issue if contained in the filter
            // if other properties are set this node is not only used for ordering purposes
            if (filter.contains(nodePath) && !node.props.isEmpty()) {
                return Collections.singleton(new ValidationMessage(severity,  String.format(MESSAGE_MISSING_PRIMARY_TYPE, nodePath)));
            }
        }
        return null;
    }

}
