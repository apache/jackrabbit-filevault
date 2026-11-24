/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.davex;

import javax.jcr.NamespaceException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import aQute.bnd.annotation.spi.ServiceProvider;
import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.client.RepositoryFactoryImpl;
import org.apache.jackrabbit.jcr2spi.Jcr2spiRepositoryFactory;
import org.apache.jackrabbit.spi.Path;
import org.apache.jackrabbit.spi.commons.conversion.PathResolver;
import org.apache.jackrabbit.spi.commons.logging.WriterLogWriterProvider;
import org.apache.jackrabbit.spi2davex.BatchReadConfig;
import org.apache.jackrabbit.spi2davex.Spi2davexRepositoryServiceFactory;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.remoterepository.ConnectionOptions;
import org.apache.jackrabbit.vault.remoterepository.spi.RepositoryFactory;
import org.jetbrains.annotations.NotNull;

/** Bridge implementation of the {@link RepositoryFactory} that uses the Jackrabbit SPI2DAVEX implementation to create a repository instance
 * for a given repository address.
 *
 * @see <a href="https://jackrabbit.apache.org/jcr/components/jackrabbit-spi.html">Jackrabbit SPI</a> */
@ServiceProvider(RepositoryFactory.class)
public class Spi2DavExRepositoryFactory implements RepositoryFactory {

    /** Name of the system property that controls the default depth for retrieving nodes via spi2davex */
    public static final String PARAM_JCR_REMOTING_DEPTH = "jcr.remoting.depth";

    /** Name of the system property that controls the spi log. */
    public static final String PARAM_JCR_REMOTING_SPILOG = "jcr.remoting.spilog";

    @Override
    public Set<String> getSupportedSchemes() {
        return Set.of("http", "https");
    }

    @Override
    public Repository createRepository(@NotNull RepositoryAddress address, @NotNull ConnectionOptions options)
            throws RepositoryException {
        return createRepository(address, toSpi2davConnectionOptions(options));
    }

    static org.apache.jackrabbit.spi2dav.ConnectionOptions toSpi2davConnectionOptions(
            @NotNull ConnectionOptions options) {
        return org.apache.jackrabbit.spi2dav.ConnectionOptions.builder()
                .useSystemProperties(options.isUseSystemProperties())
                .maxConnections(options.getMaxConnections())
                .allowSelfSignedCertificates(options.isAllowSelfSignedCertificates())
                .disableHostnameVerification(options.isDisableHostnameVerification())
                .proxyHost(options.getProxyHost())
                .proxyPort(options.getProxyPort())
                .proxyProtocol(options.getProxyProtocol())
                .proxyUsername(options.getProxyUsername())
                .proxyPassword(options.getProxyPassword())
                .connectionTimeoutMs(options.getConnectionTimeoutMs())
                .requestTimeoutMs(options.getRequestTimeoutMs())
                .socketTimeoutMs(options.getSocketTimeoutMs())
                .build();
    }

    static Repository createRepository(
            RepositoryAddress address, org.apache.jackrabbit.spi2dav.ConnectionOptions connectionOptions)
            throws RepositoryException {
        try {
            // get uri without credentials
            URI uri = address.getSpecificURI();
            if (uri.getUserInfo() != null) {
                try {
                    uri = new URI(
                            uri.getScheme(),
                            null,
                            uri.getHost(),
                            uri.getPort(),
                            uri.getPath(),
                            uri.getQuery(),
                            uri.getFragment());
                } catch (URISyntaxException e) {
                    // ignore
                }
            }
            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put(
                    Jcr2spiRepositoryFactory.PARAM_REPOSITORY_SERVICE_FACTORY,
                    Spi2davexRepositoryServiceFactory.class.getName());
            parameters.put(
                    Jcr2spiRepositoryFactory.PARAM_ITEM_CACHE_SIZE, Integer.getInteger(PARAM_JCR_REMOTING_DEPTH, 128));
            parameters.put(Spi2davexRepositoryServiceFactory.PARAM_REPOSITORY_URI, uri.toString());
            DefaultBatchReadConfig br = new DefaultBatchReadConfig();
            br.setDefaultDepth(Integer.getInteger(PARAM_JCR_REMOTING_DEPTH, 4));
            br.setDepth("/", 2);
            br.setDepth("/jcr:system", 1);
            parameters.put(Spi2davexRepositoryServiceFactory.PARAM_BATCHREAD_CONFIG, br);
            String file = System.getProperty(PARAM_JCR_REMOTING_SPILOG);
            if (file != null) {
                WriterLogWriterProvider provider = new WriterLogWriterProvider(
                        new OutputStreamWriter(FileUtils.openOutputStream(new File(file)), StandardCharsets.UTF_8));
                parameters.put(Jcr2spiRepositoryFactory.PARAM_LOG_WRITER_PROVIDER, provider);
            }

            String workspace = address.getWorkspace();
            if (workspace != null) {
                parameters.put(Spi2davexRepositoryServiceFactory.PARAM_WORKSPACE_NAME_DEFAULT, workspace);
            }
            if (connectionOptions != null) {
                parameters.putAll(connectionOptions.toServiceFactoryParameters());
            }
            return new RepositoryFactoryImpl().getRepository(parameters);
        } catch (IOException e) {
            throw new RepositoryException(e);
        }
    }

    static final class DefaultBatchReadConfig implements BatchReadConfig {

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
}
