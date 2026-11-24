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

import javax.jcr.Repository;
import javax.jcr.RepositoryException;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import aQute.bnd.annotation.spi.ServiceProvider;
import org.apache.jackrabbit.spi2dav.ConnectionOptions;
import org.apache.jackrabbit.spi2davex.Spi2davexRepositoryServiceFactory;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.RepositoryFactory;

/**
 * Implementation of the deprecated {@link org.apache.jackrabbit.vault.fs.api.RepositoryFactory} that uses the SPI2DAVEX implementation to create a repository instance for a given repository address.
 * @deprecated Use {@link Spi2davexRepositoryServiceFactory} instead.
 */
@Deprecated(forRemoval = true, since = "4.4.0")
@ServiceProvider(RepositoryFactory.class)
public class DAVExRepositoryFactory implements RepositoryFactory {

    /**
     * Name of the system property that controls the default depth for retrieving
     * nodes via spi2davex
     */
    public static final String PARAM_JCR_REMOTING_DEPTH = Spi2DavExRepositoryFactory.PARAM_JCR_REMOTING_DEPTH;

    /**
     * Name of the system property that controls the spi log.
     */
    public static final String PARAM_JCR_REMOTING_SPILOG = Spi2DavExRepositoryFactory.PARAM_JCR_REMOTING_SPILOG;

    private static final Set<String> SCHEMES = new HashSet<String>();

    static {
        SCHEMES.add("http");
        SCHEMES.add("https");
    }

    public Set<String> getSupportedSchemes() {
        return SCHEMES;
    }

    @Override
    public Repository createRepository(RepositoryAddress address) throws RepositoryException {
        return createRepository(address, null);
    }

    @Override
    public Repository createRepository(RepositoryAddress address, ConnectionOptions options)
            throws RepositoryException {
        System.out.printf(
                Locale.ENGLISH,
                "Connecting via JCR remoting to %s%n",
                address.getSpecificURI().toString());
        return Spi2DavExRepositoryFactory.createRepository(address, options);
    }
}
