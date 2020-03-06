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
package org.apache.jackrabbit.vault.validation;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;

import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.ValidatorFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A ValidationViolation is a {@link ValidationMessage} enriched with additional meta information like
 * validator id
 */
public final class ValidationViolation extends ValidationMessage {

    private final String validatorId; // may only be null if message was not bound to a validator

    public static final Collection<ValidationViolation> wrapMessages(String validatorId, Collection<? extends ValidationMessage> messages, Path filePath, Path basePath, String nodePath, int line, int column) {
        Collection<ValidationViolation> violations = new LinkedList<>();
        for (ValidationMessage message : messages) {
            violations.add(wrapMessage(validatorId, message, filePath, basePath, nodePath, line, column));
        }
        return violations;
    }

    public static final ValidationViolation wrapMessage(String validatorId, ValidationMessage message, Path filePath, Path basePath, String nodePath, int line, int column) {
        if (message instanceof ValidationViolation) {
            ValidationViolation delegate = ValidationViolation.class.cast(message);
            
            // take parameters from underlying violation and only overwrite if not set in delegate
            return new ValidationViolation(delegate.validatorId != null ? delegate.validatorId : validatorId, 
                    delegate,
                    delegate.getFilePath()!= null ? delegate.getFilePath() : filePath,
                    delegate.getBasePath() != null ? delegate.getBasePath() : basePath,
                    delegate.getNodePath() != null ? delegate.getNodePath() : nodePath, 
                    delegate.getLine() != 0 ? delegate.getLine() : line,
                    delegate.getColumn() != 0 ? delegate.getColumn() : column,
                    delegate.getThrowable());
        } else {
            return new ValidationViolation(validatorId, message, filePath, basePath, nodePath, line, column, message.getThrowable());
        }
    }

    ValidationViolation(String validatorId, ValidationMessage message, Path filePath, Path basePath, String nodePath, int line, int column, Throwable t) {
        // potentially overwrite line, column and throwable from wrapped message (but only if not yet set there)
        super(message.getSeverity(), message.getMessage(), message.getNodePath() != null ? message.getNodePath() : nodePath, message.getFilePath() != null ? message.getFilePath() : filePath, message.getBasePath() != null ? message.getBasePath() : basePath, message.getLine() != 0 ?  message.getLine() : line, message.getColumn() != 0 ? message.getColumn() : column, message.getThrowable() != null ? message.getThrowable() : t);
        
        this.validatorId = validatorId;
    }

    private ValidationViolation(String validatorId, ValidationMessage message) {
        this(validatorId, message, null, null, null, 0, 0, null);
    }

    public ValidationViolation(String validatorId, @NotNull ValidationMessageSeverity severity, @NotNull String message) {
        this(validatorId, new ValidationMessage(severity, message));
    }

    public ValidationViolation(@NotNull ValidationMessageSeverity severity, @NotNull String message) {
        this(null, new ValidationMessage(severity, message));
    }

    public ValidationViolation(@NotNull ValidationMessageSeverity severity, @NotNull String message, Path filePath, Path basePath, String nodePath, int line, int column, Throwable t) {
        this(null, new ValidationMessage(severity, message), filePath, basePath, nodePath, line, column, t);
    }

    public ValidationViolation(String validatorId, @NotNull ValidationMessageSeverity severity, @NotNull String message, Path filePath, Path basePath, String nodePath, int line, int column, Throwable t) {
        this(validatorId, new ValidationMessage(severity, message), filePath, basePath, nodePath, line, column, t);
    }

    /**
     * Returns the file path bound to this message.
     * @return the absolute file path or {@code null} if the message does not belong to a file
     */
    public @Nullable Path getAbsoluteFilePath() {
        Path basePath = getBasePath();
        if (basePath != null && getFilePath() != null) {
            return basePath.resolve(getFilePath());
        }
        return getFilePath();
    }

    /**
     * Returns the validator id bound to this message.
     * @return the validator id or {@code null} if the message does not belong to a specific {@link ValidatorFactory}
     */
    public @Nullable String getValidatorId() {
        return validatorId;
    }

    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((validatorId == null) ? 0 : validatorId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        ValidationViolation other = (ValidationViolation) obj;
        if (validatorId == null) {
            if (other.validatorId != null)
                return false;
        } else if (!validatorId.equals(other.validatorId))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "ValidationViolation [" 
                + (validatorId != null ? "validatorId=" + validatorId + ", " : "")
                + "super=" + super.toString() + "]";
    }
}
