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

import java.nio.file.Path;

/**
 * Exception wrapping all sorts of runtime exceptions being thrown from a validator
 */
public class ValidatorException extends RuntimeException {

    private ValidatorException(String id, String messageSuffix, Throwable cause) {
        super("Exception in validator '" + id + "'" + messageSuffix + ": " + cause.getMessage(), cause);
    }

    public ValidatorException(String id, Throwable cause) {
        this(id, "", cause);
    }

    public ValidatorException(String id, RuntimeException e, Path filePath, int lineNumber, int columnNumber, Throwable cause) {
        this(id, " while validating file '" + filePath + "' (line " + lineNumber + ", column " + columnNumber + ")", cause);
    }

    public ValidatorException(String id, Path filePath, Throwable cause) {
        this(id, " while validating file '" + filePath + "'", cause);
    }

    public ValidatorException(String id, String nodePath, Path filePath, Throwable cause) {
        this(id, " while validating node path " + nodePath + " (" + filePath + ")", cause);
    }
}
