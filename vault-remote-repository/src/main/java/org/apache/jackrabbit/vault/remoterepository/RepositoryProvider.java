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
package org.apache.jackrabbit.vault.remoterepository;

import javax.jcr.Repository;
import javax.jcr.RepositoryException;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;

import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.remoterepository.spi.RepositoryFactory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses the ServiceLoader mechanism to load all available RepositoryFactory implementations and provides a repository instance for a given repository address.
 * The first RepositoryFactory implementation that supports the scheme of the given repository address is used to create the repository instance.
 * In addition it provides a caching mechanism to avoid creating multiple repository instances for the same repository address.
 *
 */
public final class RepositoryProvider {

    protected static Logger log = LoggerFactory.getLogger(RepositoryProvider.class);

    private Map<RepositoryAddress, Repository> repos = new HashMap<RepositoryAddress, Repository>();

    public Repository getRepository(@NotNull RepositoryAddress address, @NotNull ConnectionOptions options)
            throws RepositoryException {
        Repository rep = repos.get(address);
        if (rep == null) {
            rep = createRepository(address, options);
            repos.put(address, rep);
        }
        return rep;
    }

    private Repository createRepository(@NotNull RepositoryAddress address, @NotNull ConnectionOptions options)
            throws RepositoryException {
        ServiceLoader<RepositoryFactory> loader = ServiceLoader.load(RepositoryFactory.class);
        Iterator<RepositoryFactory> iter = loader.iterator();
        Set<String> supported = new HashSet<String>();
        while (iter.hasNext()) {
            RepositoryFactory fac = iter.next();
            supported.addAll(fac.getSupportedSchemes());
            Repository rep = fac.createRepository(address, options);
            if (rep != null) {
                // wrap JCR logger
                if (Boolean.getBoolean("jcrlog.sysout") || System.getProperty("jcrlog.file") != null) {
                    Repository wrapped = wrapLogger(rep, address);
                    if (wrapped != null) {
                        log.info("Enabling JCR Logger.");
                        rep = wrapped;
                    }
                }
                return rep;
            }
        }
        StringBuffer msg = new StringBuffer("URL scheme ");
        msg.append(address.getURI().getScheme());
        msg.append(" not supported. only");
        for (String s : supported) {
            msg.append(" ").append(s);
        }
        throw new RepositoryException(msg.toString());
    }

    private Repository wrapLogger(Repository base, RepositoryAddress address) {
        try {
            // this would be provided by sandbox project https://svn.apache.org/repos/asf/jackrabbit/sandbox/jcrlog/src/main/java/org/apache/jackrabbit/jcrlog/RepositoryLogger.java
            Class clazz = getClass().getClassLoader().loadClass("org.apache.jackrabbit.jcrlog.RepositoryLogger");
            // just map all properties
            Properties props = new Properties();
            for (Object o : System.getProperties().keySet()) {
                String name = o.toString();
                if (name.startsWith("jcrlog.")) {
                    props.put(name.substring("jcrlog.".length()), System.getProperty(name));
                }
            }
            Constructor c = clazz.getConstructor(Repository.class, Properties.class, String.class);
            return (Repository) c.newInstance(base, props, address.toString());
        } catch (Exception e) {
            log.error("Unable to initialize JCR logger: {}", e.toString());
            return null;
        }
    }
}
