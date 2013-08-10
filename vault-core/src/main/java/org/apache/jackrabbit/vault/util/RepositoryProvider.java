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

package org.apache.jackrabbit.vault.util;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.imageio.spi.ServiceRegistry;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.RepositoryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>RepositoryProvider</code>...
 *
 */
public class RepositoryProvider {

    protected static Logger log = LoggerFactory.getLogger(RepositoryProvider.class);

    private Map<RepositoryAddress, Repository> repos
            = new HashMap<RepositoryAddress, Repository>();

    public Repository getRepository(RepositoryAddress address)
            throws RepositoryException {
        Repository rep = repos.get(address);
        if (rep == null) {
            rep = createRepository(address);
            repos.put(address, rep);
        }
        return rep;
    }

    private Repository createRepository(RepositoryAddress address)
            throws RepositoryException {
        Iterator<RepositoryFactory> iter = ServiceRegistry.lookupProviders(RepositoryFactory.class);
        Set<String> supported = new HashSet<String>();
        while (iter.hasNext()) {
            RepositoryFactory fac = iter.next();
            supported.addAll(fac.getSupportedSchemes());
            Repository rep = fac.createRepository(address);
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
        for (String s: supported) {
            msg.append(" ").append(s);
        }
        throw new RepositoryException(msg.toString());
    }

    private Repository wrapLogger(Repository base,RepositoryAddress address) {
        try {
            Class clazz = getClass().getClassLoader().loadClass("org.apache.jackrabbit.jcrlog.RepositoryLogger");
            // just map all properties
            Properties props = new Properties();
            for (Object o: System.getProperties().keySet()) {
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