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
package org.apache.jackrabbit.vault.validation.spi;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Class encapsulating the actual message together with a {@link ValidationMessageSeverity}.
 */
public class ValidationMessage {

    private final ValidationMessageSeverity severity;
    private final String message;
    /** if this is file based, may indicate the line where the issue happened, 0 for unspecified */
    private final int line;
    /** if this is file based, may indicate the column where the issue happened, 0 for unspecified */
    private final int column;
    /** the underlying exception if there was any, may be null */
    private final Throwable throwable;
    
    public ValidationMessage(@Nonnull ValidationMessageSeverity severity, @Nonnull String message) {
        this(severity, message, 0, 0, null);
    }

    public ValidationMessage(@Nonnull ValidationMessageSeverity severity, @Nonnull String message, Throwable throwable) {
        this(severity, message, 0, 0, throwable);
    }

    public ValidationMessage(@Nonnull ValidationMessageSeverity severity, @Nonnull String message, int line, int column, Throwable throwable) {
        this.severity = severity;
        this.message = message;
        this.line = line;
        this.column = column;
        this.throwable = throwable;
    }
    
    
    
    /**
     * Returns the severity of this message.
     * @return the severity of this message
     */
    public @Nonnull ValidationMessageSeverity getSeverity() {
        return severity;
    }

    /**
     * Returns the message text.
     * @return the message text
     */
    public @Nonnull String getMessage() {
        return message;
    }

    /** 
     * Returns the line number.
     * @return the line number of this violation (1-based) or 0 if not bound to any specific line number
     */
    public int getLine() {
        return line;
    }

    /** 
     * Returns the column number.
     * @return the column number of this violation (1-based) or 0 if not bound to any specific column number
     */
    public int getColumn() {
        return column;
    }

    /**
     * Returns the underlying throwable.
     * @return the throwable bound to this message or {@code null} if the message has no underlying throwable
     */
    public @CheckForNull Throwable getThrowable() {
        return throwable;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + column;
        result = prime * result + line;
        result = prime * result + ((message == null) ? 0 : message.hashCode());
        result = prime * result + ((severity == null) ? 0 : severity.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ValidationMessage other = (ValidationMessage) obj;
        if (column != other.column)
            return false;
        if (line != other.line)
            return false;
        if (message == null) {
            if (other.message != null)
                return false;
        } else if (!message.equals(other.message))
            return false;
        if (severity != other.severity)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "ValidationMessage [" + (severity != null ? "severity=" + severity + ", " : "")
                + (message != null ? "message=" + message + ", " : "") + "line=" + line + ", column=" + column + ", "
                + (throwable != null ? "throwable=" + throwable : "") + "]";
    }

}
