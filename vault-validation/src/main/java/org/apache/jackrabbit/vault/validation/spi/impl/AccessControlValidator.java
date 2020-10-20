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

import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.JcrACLManagement;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see <a href="https://issues.apache.org/jira/browse/JCRVLT-255">JCRVLT-255</a>
 */
public class AccessControlValidator implements DocumentViewXmlValidator {

    protected static final JcrACLManagement ACL_MANAGEMENT = new JcrACLManagement();
    protected static final String MESSAGE_IGNORED_ACCESS_CONTROL_LIST = "Found an access control list, but it is never considered during installation as the property 'acHandling' is set to 'ignore' or 'clear'!";
    private final ValidationMessageSeverity severity;
    private final AccessControlHandling accessControlHandling;

    public AccessControlValidator(ValidationMessageSeverity severity, AccessControlHandling accessControlHandling) {
        super();
        this.severity = severity;
        this.accessControlHandling = accessControlHandling;
    }

    @Override
    public Collection<ValidationMessage> done() {
        return null;
    }

    @Override
    public @Nullable Collection<ValidationMessage> validate(@NotNull DocViewNode node, @NotNull NodeContext nodeContext,
            boolean isRoot) {
        // extract primary type
        if ((accessControlHandling == AccessControlHandling.IGNORE || accessControlHandling == AccessControlHandling.CLEAR) && node.primary != null && ACL_MANAGEMENT.isACLNodeType(node.primary)) {
            return Collections.singleton(new ValidationMessage(severity, MESSAGE_IGNORED_ACCESS_CONTROL_LIST));
        }
        return null;
    }

}
