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

package org.apache.jackrabbit.vault.fs.impl.io;

import java.io.IOException;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.apache.jackrabbit.vault.fs.api.ArtifactHandler;
import org.apache.jackrabbit.vault.fs.api.ArtifactSet;
import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.Dumpable;
import org.apache.jackrabbit.vault.fs.api.ImportInfo;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.impl.ArtifactSetImpl;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.spi.ACLManagement;
import org.apache.jackrabbit.vault.fs.spi.ServiceProviderFactory;

/**
 * <code>AbstractArtifactHandler</code>...
 *
 */
public abstract class AbstractArtifactHandler implements ArtifactHandler, Dumpable {

    /**
     * access control handling.
     * todo: would be better to pass via some kind of import context
     */
    protected AccessControlHandling acHandling = AccessControlHandling.OVERWRITE;

    /**
     * acl management
     */
    private ACLManagement aclManagement;

    /**
     * Returns the access control handling defined for this handler
     * @return the access control handling.
     */
    public AccessControlHandling getAcHandling() {
        return acHandling;
    }

    /**
     * Sets the access control handling used for importing.
     * @param acHandling the access control handling
     */
    public void setAcHandling(AccessControlHandling acHandling) {
        this.acHandling = acHandling;
    }

    /**
     * Returns the ACL management
     * @return the ACL management
     */
    public ACLManagement getAclManagement() {
        if (aclManagement == null) {
            aclManagement = ServiceProviderFactory.getProvider().getACLManagement();
        }
        return aclManagement;
    }

    /**
     * {@inheritDoc}
     */
    public ImportInfo accept(Session session, Aggregate file,
                             ArtifactSet artifacts)
            throws RepositoryException, IOException {
        Node node = file.getNode();
        String name = node.getName();
        return accept(file.getManager().getWorkspaceFilter(),
                name.length() == 0 ? node : node.getParent(),
                name, (ArtifactSetImpl) artifacts);
    }

    /**
     * {@inheritDoc}
     */
    public ImportInfo accept(Session session, Aggregate parent, String name,
                             ArtifactSet artifacts)
            throws RepositoryException, IOException {
        Node node = parent.getNode();
        return accept(parent.getManager().getWorkspaceFilter(),
                node, name, (ArtifactSetImpl) artifacts);
    }

    /**
     * Imports an artifact set below the node.
     *
     * @param wspFilter the workspace filter
     * @param parent the parent node
     * @param name the name of the (new) import
     * @param artifacts the artifact set
     * @return the import info on successful import, <code>null</code> otherwise
     * @throws RepositoryException if an error occurs.
     * @throws IOException if an I/O error occurs.
     */
    protected abstract ImportInfoImpl accept(WorkspaceFilter wspFilter, Node parent,
                                         String name, ArtifactSetImpl artifacts)
            throws RepositoryException, IOException;

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        ctx.println(isLast, getClass().getSimpleName());
    }

}