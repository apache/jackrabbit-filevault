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
package org.apache.jackrabbit.vault.vlt;

/**
 * <code>VaultException</code>...
 *
 */
public class VltException extends Exception {

    static final long serialVersionUID = -4355975803798981445L;

    private final String path;

    private final boolean isUserError;

    public VltException(String message) {
        this(null, false, message, null);
    }

    public VltException(String path, String message) {
        this(path, false, message, null);
    }

    public VltException(String message, Throwable cause) {
        this(null, false, message, cause);
    }

    public VltException(String path, String message, Throwable cause) {
        this(path, false, message, cause);
    }
    
    public VltException(String path, boolean isUserError, String message, Throwable cause) {
        super(message, cause);
        this.path = path;
        this.isUserError = isUserError;
    }

    public String getPath() {
        return path;
    }

    public boolean isUserError() {
        return isUserError;
    }

    public String message() {
        return super.getMessage();
    }
    
    public String getMessage() {
        return path == null || path.equals("") || path.equals(".")
                ? super.getMessage()
                : path + ": " + super.getMessage();
    }
}