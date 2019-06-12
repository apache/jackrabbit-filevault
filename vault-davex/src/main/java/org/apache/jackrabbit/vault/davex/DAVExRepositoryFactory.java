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
package org.apache.jackrabbit.vault.davex;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.jcr.NamespaceException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.client.RepositoryFactoryImpl;
import org.apache.jackrabbit.jcr2spi.Jcr2spiRepositoryFactory;
import org.apache.jackrabbit.spi.Path;
import org.apache.jackrabbit.spi.commons.conversion.PathResolver;
import org.apache.jackrabbit.spi.commons.logging.WriterLogWriterProvider;
import org.apache.jackrabbit.spi2davex.BatchReadConfig;
import org.apache.jackrabbit.spi2davex.Spi2davexRepositoryServiceFactory;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.RepositoryFactory;

/**
 * {@code DAVExRepositoryFactory}...
 */
public class DAVExRepositoryFactory implements RepositoryFactory {

    /**
     * Name of the system property that controls the default depth for retrieving
     * nodes via spi2davex
     */
    public static final String PARAM_JCR_REMOTING_DEPTH = "jcr.remoting.depth";

    /**
     * Name of the system property that controls the spi log.
     */
    public static final String PARAM_JCR_REMOTING_SPILOG = "jcr.remoting.spilog";

    private static final Set<String> SCHEMES = new HashSet<String>();
    static {
        SCHEMES.add("http");
        SCHEMES.add("https");
    }

    public Set<String> getSupportedSchemes() {
        return SCHEMES;
    }
    public Repository createRepository(RepositoryAddress address)
            throws RepositoryException {
        return this.createRepository(address, false);
    }
    public Repository createRepository(RepositoryAddress address, boolean allowInsecureHttps)
            throws RepositoryException {
        if (!SCHEMES.contains(address.getSpecificURI().getScheme())) {
            return null;
        }
        try {
            // get uri without credentials
            URI uri = address.getSpecificURI();
            if (uri.getUserInfo() != null) {
                try {
                    uri = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
                } catch (URISyntaxException e) {
                    // ignore
                }
            }
            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put(Jcr2spiRepositoryFactory.PARAM_REPOSITORY_SERVICE_FACTORY, Spi2davexRepositoryServiceFactory.class.getName());
            parameters.put(Jcr2spiRepositoryFactory.PARAM_ITEM_CACHE_SIZE, Integer.getInteger(PARAM_JCR_REMOTING_DEPTH, 128));
            parameters.put(Spi2davexRepositoryServiceFactory.PARAM_REPOSITORY_URI, uri.toString());
            DefaultBatchReadConfig br = new DefaultBatchReadConfig();
            br.setDefaultDepth(Integer.getInteger(PARAM_JCR_REMOTING_DEPTH, 4));
            br.setDepth("/", 2);
            br.setDepth("/jcr:system", 1);
            parameters.put(Spi2davexRepositoryServiceFactory.PARAM_BATCHREAD_CONFIG, br);
            String file = System.getProperty(PARAM_JCR_REMOTING_SPILOG);
            if (file != null) {
                WriterLogWriterProvider provider = new WriterLogWriterProvider(
                        new OutputStreamWriter(FileUtils.openOutputStream(new File(file)))
                );
                parameters.put(
                        Jcr2spiRepositoryFactory.PARAM_LOG_WRITER_PROVIDER,
                        provider
                );
            }

            String workspace = address.getWorkspace();
            if (workspace != null) {
                parameters.put(Spi2davexRepositoryServiceFactory.PARAM_WORKSPACE_NAME_DEFAULT, workspace);
            }
            if (allowInsecureHttps && uri.getScheme().equals("https")) {
                parameters.put(Spi2davexRepositoryServiceFactory.PARAM_REPOSITORY_URI_INSECURE, true);
            }
            System.out.printf("Connecting via JCR remoting to %s%n", address.getSpecificURI().toString());
            return new RepositoryFactoryImpl().getRepository(parameters);
        } catch (IOException e) {
            throw new RepositoryException(e);
        }
    }
}

class DefaultBatchReadConfig implements BatchReadConfig {

    public static final int DEPTH_INFINITE = -1;

    private final Map<String, Integer> depthMap = new HashMap<String, Integer>();

    private int defaultDepth = 0;

    public int getDepth(Path path, PathResolver resolver) throws NamespaceException {
        String jcrPath = resolver.getJCRPath(path);
        Integer depth = depthMap.get(jcrPath);
        return depth == null ? defaultDepth : depth;
    }

    public void setDepth(String path, int depth) {
        depthMap.put(path, depth);
    }

    public void setDefaultDepth(int defaultDepth) {
        this.defaultDepth = defaultDepth;
    }
}
