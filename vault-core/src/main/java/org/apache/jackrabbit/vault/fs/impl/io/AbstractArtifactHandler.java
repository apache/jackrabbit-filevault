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
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.apache.jackrabbit.vault.fs.api.ArtifactHandler;
import org.apache.jackrabbit.vault.fs.api.ArtifactSet;
import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.Dumpable;
import org.apache.jackrabbit.vault.fs.api.IdConflictPolicy;
import org.apache.jackrabbit.vault.fs.api.ImportInfo;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.impl.ArtifactSetImpl;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.DocViewParser;
import org.apache.jackrabbit.vault.fs.io.DocViewParser.XmlParseException;
import org.apache.jackrabbit.vault.fs.io.DocViewParserHandler;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.spi.ACLManagement;
import org.apache.jackrabbit.vault.fs.spi.ServiceProviderFactory;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * {@code AbstractArtifactHandler}...
 *
 */
public abstract class AbstractArtifactHandler implements ArtifactHandler, Dumpable {

    /**
     * access control handling.
     * todo: would be better to pass via some kind of import context
     */
    protected AccessControlHandling acHandling = AccessControlHandling.OVERWRITE;

    /**
     * Closed user group (CUG) handling. <code>null</code> indicates that
     * the handling is governed by acHandling values.
     * todo: would be better to pass via some kind of import context
     */
    protected AccessControlHandling cugHandling = null;

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
     * Returns closed user group (CUG) handling defined for this handler
     * @return the access control handling.
     */
    public AccessControlHandling getCugHandling() {
        return cugHandling;
    }

    /**
     * Sets closed user group (CUG) handling used for importing.
     * @param cugHandling the access control handling
     *                    When <code>null</code> value is specified
     *                    CUG handling is controled by acHandling value.
     */
    public void setCugHandling(AccessControlHandling cugHandling) {
        this.cugHandling = cugHandling;
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
        return accept(new ImportOptions(), file.getManager().getWorkspaceFilter(),
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
        return accept(new ImportOptions(), parent.getManager().getWorkspaceFilter(),
                node, name, (ArtifactSetImpl) artifacts);
    }

    /**
     * Imports an artifact set below the node.
     *
     * @param options the import options
     * @param wspFilter the workspace filter
     * @param parent the parent node
     * @param name the name of the (new) import
     * @param artifacts the artifact set
     * @return the import info on successful import, {@code null} otherwise
     * @throws RepositoryException if an error occurs.
     * @throws IOException if an I/O error occurs.
     */
    protected abstract ImportInfoImpl accept(@NotNull ImportOptions options, WorkspaceFilter wspFilter, Node parent,
                                         String name, ArtifactSetImpl artifacts)
            throws RepositoryException, IOException;

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        ctx.println(isLast, getClass().getSimpleName());
    }

    protected ImportInfoImpl importDocView(InputSource source, Node parentNode, String rootNodeName, ArtifactSetImpl artifacts, WorkspaceFilter wspFilter, IdConflictPolicy idConflictPolicy) throws IOException, RepositoryException {
        DocViewImporter handler = new DocViewImporter(parentNode, rootNodeName, artifacts, wspFilter, idConflictPolicy, getAcHandling(), getCugHandling());
        try {
            String rootNodePath;
            if (parentNode != null) {
                rootNodePath = parentNode.getPath();
                if (!rootNodePath.equals("/")) {
                    rootNodePath += "/";
                }
                rootNodePath += rootNodeName;
            } else {
                rootNodePath = "/";
            }
            
            new DocViewParser().parse(rootNodePath, source, handler);
        } catch (XmlParseException e) {
            // wrap as repositoryException although not semantically correct for backwards compatibility
            throw new RepositoryException(e);
        }
        return handler.getInfo();
    }
}