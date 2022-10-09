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
package org.apache.jackrabbit.vault.validation.impl.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class ValidationMessageErrorHandler
        extends DefaultHandler {

    private final Set<ValidationMessage> messages;
    private final ValidationMessageSeverity errorMessageSeverity;

    public ValidationMessageErrorHandler(ValidationMessageSeverity errorMessageSeverity) {
        messages = new LinkedHashSet<>();
        this.errorMessageSeverity = errorMessageSeverity;
    }

    private void print(@NotNull SAXParseException x, @NotNull ValidationMessageSeverity severity) {
        ValidationMessage message = new ValidationMessage(severity, x.getMessage(), Integer.valueOf(x.getLineNumber()),
                Integer.valueOf(x.getColumnNumber()), null);
        messages.add(message);
    }

    @Override
    public void warning(SAXParseException x) {
        print(x, ValidationMessageSeverity.WARN);
    }

    @Override
    public void error(SAXParseException x) {
        print(x, errorMessageSeverity);
    }

    @Override
    public void fatalError(SAXParseException x)
            throws SAXParseException {
        print(x, ValidationMessageSeverity.ERROR);
        throw x;
    }

    public Collection<ValidationMessage> getValidationMessages() {
        return messages;
    }
}