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

package org.apache.jackrabbit.vault.fs.api;

import java.io.IOException;
import java.util.Collection;

import javax.jcr.RepositoryException;

/**
 * <code>VaultFsTransaction</code>...
 */
public interface VaultFsTransaction {

    boolean isVerbose();

    void setVerbose(boolean verbose);

    void delete(VaultFile file) throws IOException;

    void modify(VaultFile file, VaultInputSource input) throws IOException;

    VaultFileOutput add(String path, VaultInputSource input)
        throws IOException, RepositoryException;

    void mkdir(String path) throws IOException, RepositoryException;

    /**
     * Commits the transaction and uploads all modifications to the repository.
     *
     * @return a list of modifications
     * @throws IOException if an I/O error occurs
     * @throws RepositoryException if a repository error occurs
     */
    Collection<Info> commit() throws RepositoryException, IOException;

    public enum Type {
        ADDED, ADDED_X, DELETED, MODIFIED, MOVED, MKDIR, ERROR
    }

    /**
     * the transaction info
     */
    public static class Info {

        private final Type type;

        private String path;

        public Info(Type type, String path) {
            this.type = type;
            this.path = path;
        }

        public String getPath() {
            return path;
        }

        public Type getType() {
            return type;
        }

    }
}