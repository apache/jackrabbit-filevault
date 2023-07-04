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
package org.apache.jackrabbit.vault.packaging.integration;

import java.io.IOException;
import java.util.Map;

import javax.jcr.Repository;
import javax.jcr.RepositoryException;

public interface RepositoryProvider {

    public final static class RepositoryWithMetadata {
        private final Repository repository;
        private final Map<String, Object> metadata;
        public RepositoryWithMetadata(Repository repository, Map<String, Object> metadata) {
            super();
            this.repository = repository;
            this.metadata = metadata;
        }
        public Repository getRepository() {
            return repository;
        }
        public Map<String, Object> getMetadata() {
            return metadata;
        }
    }

    RepositoryWithMetadata createRepository(boolean useFileStore, boolean enablePrincipalBasedAuthorization, String... cugEnabledPaths) throws RepositoryException, IOException;
    void closeRepository(RepositoryWithMetadata repositoryWithMetadata) throws RepositoryException, IOException;
    /**
     * 
     * @return the repository base path for all service users (based on principals). Requires a repository created via {@link #createRepository(boolean, boolean)} with the second argument being {@code true}.
     */
    String getServiceUserPath();
    /**
     * 
     * @return true in case this provider creates Oak repositories, false otherwise
     */
    boolean isOak();
}
