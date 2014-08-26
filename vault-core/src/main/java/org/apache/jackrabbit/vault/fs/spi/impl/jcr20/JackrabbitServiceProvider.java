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

import java.io.Writer;
import java.util.Set;

import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.spi.ACLManagement;
import org.apache.jackrabbit.vault.fs.spi.CNDReader;
import org.apache.jackrabbit.vault.fs.spi.CNDWriter;
import org.apache.jackrabbit.vault.fs.spi.DefaultNodeTypes;
import org.apache.jackrabbit.vault.fs.spi.JcrVersion;
import org.apache.jackrabbit.vault.fs.spi.NodeTypeInstaller;
import org.apache.jackrabbit.vault.fs.spi.PrivilegeInstaller;
import org.apache.jackrabbit.vault.fs.spi.ServiceProvider;
import org.apache.jackrabbit.vault.fs.spi.UserManagement;

/**
 * Implements a Service Provider for JCR 2.0 Repositories
 */
public class JackrabbitServiceProvider implements ServiceProvider {

    private ACLManagement aclManagement;

    private UserManagement userManagement;

    /**
     * {@inheritDoc}
     */
    public JcrVersion getJCRVersion() {
        return JcrVersion.V20;
    }

    /**
     * {@inheritDoc}
     */
    public Set<String> getBuiltInNodeTypeNames() {
        return DefaultNodeTypes.CRX_2X_NODE_TYPES;
    }

    /**
     * {@inheritDoc}
     */
    public NodeTypeInstaller getDefaultNodeTypeInstaller(Session session) {
        return new JcrNodeTypeInstaller(session);
    }

    /**
     * {@inheritDoc}
     */
    public PrivilegeInstaller getDefaultPrivilegeInstaller(Session session) {
        return new JackrabbitPrivilegeInstaller(session);
    }

    /**
     * {@inheritDoc}
     */
    public CNDReader getCNDReader() {
        return new DefaultCNDReader();
    }

    /**
     * {@inheritDoc}
     */
    public CNDWriter getCNDWriter(Writer out, Session s, boolean includeNS) {
        return new DefaultCNDWriter(out, s, includeNS);
    }

    /**
     * {@inheritDoc}
     */
    public ACLManagement getACLManagement() {
        if (aclManagement == null) {
            aclManagement = new JcrACLManagement();
        }
        return aclManagement;
    }

    /**
     * {@inheritDoc}
     */
    public UserManagement getUserManagement() {
        if (userManagement == null) {
            userManagement = new JackrabbitUserManagement();
        }
        return userManagement;
    }
}