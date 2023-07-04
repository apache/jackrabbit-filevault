package org.apache.jackrabbit.vault.it;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.jcr.Repository;
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
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.apache.jackrabbit.vault.packaging.integration.IntegrationTestBase;
import org.apache.jackrabbit.vault.packaging.integration.RepositoryProvider;
import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@MetaInfServices
public class JackrabbitRepositoryProvider implements RepositoryProvider {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(IntegrationTestBase.class);

    private static final File DIR_JR2_REPO_HOME = new File("target", "repository-jr2-" + System.getProperty("repoSuffix", "fork1"));

    @Override
    public RepositoryWithMetadata createRepository(boolean arg0, boolean arg1, String... arg2) throws RepositoryException, IOException {
        Repository repository;
        try (InputStream in = IntegrationTestBase.class.getResourceAsStream("/repository.xml")) {
            RepositoryConfig cfg = RepositoryConfig.create(in, DIR_JR2_REPO_HOME.getPath());
            repository = RepositoryImpl.create(cfg);
        }
        return new RepositoryWithMetadata(repository, null);
    }

    @Override
    public void closeRepository(RepositoryWithMetadata repositoryWithMetadata) throws IOException {
        ((RepositoryImpl) repositoryWithMetadata.getRepository()).shutdown();
        IntegrationTestBase.deleteDirectory(DIR_JR2_REPO_HOME);
    }

    @Override
    public String getServiceUserPath() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOak() {
        return false;
    }
}
