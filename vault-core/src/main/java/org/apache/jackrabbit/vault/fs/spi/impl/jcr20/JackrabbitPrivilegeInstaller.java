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

package org.apache.jackrabbit.vault.fs.spi.impl.jcr20;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.api.JackrabbitWorkspace;
import org.apache.jackrabbit.api.security.authorization.PrivilegeManager;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.PrivilegeDefinition;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.spi.PrivilegeDefinitions;
import org.apache.jackrabbit.vault.fs.spi.PrivilegeInstaller;
import org.apache.jackrabbit.vault.fs.spi.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>JcrNodeTypeInstaller</code> is used to install privileges using the
 * jackrabbit privilege manager
 */
public class JackrabbitPrivilegeInstaller implements PrivilegeInstaller {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(JackrabbitPrivilegeInstaller.class);

    private final Session session;

    public JackrabbitPrivilegeInstaller(Session session) {
        this.session = session;
    }

    /**
     * {@inheritDoc}
     */
    public Collection<Privilege> install(ProgressTracker tracker, PrivilegeDefinitions defs)
            throws IOException, RepositoryException {

        Workspace wsp = session.getWorkspace();
        if (!(wsp instanceof JackrabbitWorkspace)) {
            throw new RepositoryException("Unable to register privileges. No JackrabbitWorkspace.");
        }
        PrivilegeManager mgr = ((JackrabbitWorkspace) wsp).getPrivilegeManager();

        ProgressTrackerListener.Mode mode = null;
        if (tracker != null) {
            mode = tracker.setMode(ProgressTrackerListener.Mode.TEXT);
        }

        // register namespaces
        Map<String, String> pfxToURI = defs.getNamespaceMapping().getPrefixToURIMapping();
        if (!pfxToURI.isEmpty()) {
            for (Object o : pfxToURI.keySet()) {
                String prefix = (String) o;
                String uri = pfxToURI.get(prefix);
                try {
                    session.getNamespacePrefix(uri);
                    track(tracker, "-", prefix + " -> " + uri);
                } catch (RepositoryException e) {
                    session.getWorkspace().getNamespaceRegistry().registerNamespace(prefix, uri);
                    track(tracker, "A", prefix + " -> " + uri);
                }
            }
        }

        // register node types
        List<Privilege> registeredPrivs = new LinkedList<Privilege>();
        for (PrivilegeDefinition def: defs.getDefinitions()) {
            String name = getJCRName(def.getName());
            Privilege priv = null;
            try {
                priv = mgr.getPrivilege(name);
            } catch (RepositoryException e) {
                // ignore, already registered
            }
            if (priv == null) {
                String[] aggregateNames = new String[def.getDeclaredAggregateNames().size()];
                int i=0;
                for (Name n: def.getDeclaredAggregateNames()) {
                    aggregateNames[i++] = getJCRName(n);
                }
                registeredPrivs.add(mgr.registerPrivilege(name, def.isAbstract(), aggregateNames));
                track(tracker, "A", name);
            } else {
                track(tracker, "-", name);
            }
        }

        if (tracker != null) {
            tracker.setMode(mode);
        }
        return registeredPrivs;
    }

    private void track(ProgressTracker tracker, String action, String path) {
        log.debug("{} {}", action, path);
        if (tracker != null) {
            tracker.track(action, path);
        }
    }

    private String getJCRName(Name name) {
        StringBuilder str = new StringBuilder("{");
        str.append(name.getNamespaceURI());
        str.append("}");
        str.append(name.getLocalName());
        return str.toString();
    }
}