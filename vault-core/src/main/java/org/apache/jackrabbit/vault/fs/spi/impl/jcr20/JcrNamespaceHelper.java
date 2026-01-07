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
package org.apache.jackrabbit.vault.fs.spi.impl.jcr20;

import javax.jcr.NamespaceException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import java.util.Map;

import org.apache.jackrabbit.vault.fs.spi.ProgressTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JcrNamespaceHelper {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(JcrNamespaceHelper.class);

    private final Session session;

    private final ProgressTracker tracker;

    public JcrNamespaceHelper(@NotNull Session session, @Nullable ProgressTracker tracker) {
        this.session = session;
        this.tracker = tracker;
    }

    /**
     * Registers a map of namespaces
     * @param pfxToURI map from prefix to uri mappings
     * @throws RepositoryException if an error occurs
     */
    public void registerNamespaces(@NotNull Map<String, String> pfxToURI) throws RepositoryException {
        if (!pfxToURI.isEmpty()) {
            for (Object o : pfxToURI.keySet()) {
                String prefix = (String) o;
                String uri = pfxToURI.get(prefix);
                try {
                    session.getNamespacePrefix(uri);
                    track(tracker, "-", prefix + " -> " + uri);
                } catch (RepositoryException e) {
                    registerNamespace(prefix, uri);
                }
            }
        }
    }

    /**
     * Attempts to register a namespace
     * @param pfxHint prefix to use if possible
     * @param uri uri to register
     * @return the registered prefix
     * @throws RepositoryException if an error occurs
     */
    @NotNull
    public String registerNamespace(@NotNull String pfxHint, @NotNull String uri) throws RepositoryException {
        int i = 0;
        String pfx = pfxHint;
        Throwable error = null;
        while (i < 1000) {
            try {
                session.getWorkspace().getNamespaceRegistry().registerNamespace(pfx, uri);
                track(tracker, "A", pfx + " -> " + uri);
                return pfx;
            } catch (NamespaceException e) {
                pfx = pfxHint + i++;
                error = e;
            }
        }
        throw new RepositoryException("Giving up automatic namespace registration after 1000 attempts.", error);
    }

    private void track(ProgressTracker tracker, String action, String path) {
        log.debug("{} {}", action, path);
        if (tracker != null) {
            tracker.track(action, path);
        }
    }
}
