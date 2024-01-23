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
import org.apache.jackrabbit.vault.fs.api.IdConflictPolicy;
import org.apache.jackrabbit.vault.fs.api.ImportInfo;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.impl.ArtifactSetImpl;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.DocViewParser;
import org.apache.jackrabbit.vault.fs.io.DocViewParser.XmlParseException;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.spi.ACLManagement;
import org.apache.jackrabbit.vault.fs.spi.ServiceProviderFactory;
import org.apache.jackrabbit.vault.packaging.UncoveredAncestorHandling;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

/**
 * {@code AbstractArtifactHandler}...
 *
 */
public abstract class AbstractArtifactHandler implements ArtifactHandler, Dumpable {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(AbstractArtifactHandler.class);

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

    protected UncoveredAncestorHandling uncoveredAncestorHandling = null;

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

    public UncoveredAncestorHandling getUncoveredAncestorHandling() {
        return uncoveredAncestorHandling;
    }

    public void setUncoveredAncestorHandling(UncoveredAncestorHandling uncoveredAncestorHandling) {
        this.uncoveredAncestorHandling = uncoveredAncestorHandling;
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

    @Override
    public ImportInfo accept(Session session, Aggregate file,
                             ArtifactSet artifacts)
            throws RepositoryException, IOException {
        Node node = file.getNode();
        String name = node.getName();
        return accept(new ImportOptions(), true, file.getManager().getWorkspaceFilter(),
                name.length() == 0 ? node : node.getParent(),
                name, (ArtifactSetImpl) artifacts);
    }

    @Override
    public ImportInfo accept(Session session, Aggregate parent, String name,
                             ArtifactSet artifacts)
            throws RepositoryException, IOException {
        Node node = parent.getNode();
        return accept(new ImportOptions(), true, parent.getManager().getWorkspaceFilter(),
                node, name, (ArtifactSetImpl) artifacts);
    }

    /**
     * Imports an artifact set below the node.
     * This method is not part of the public API!
     *
     * @param options the import options
     * @param isStrictByDefault whether the package should be imported with strict setting by default (i.e. in case ImportOptions has no explicit other flag value set)
     * @param wspFilter the workspace filter
     * @param parent the parent node
     * @param name the name of the (new) import
     * @param artifacts the artifact set
     * @return the import info on successful import, {@code null} otherwise
     * @throws RepositoryException if an error occurs.
     * @throws IOException if an I/O error occurs.
     */
    public abstract ImportInfoImpl accept(@NotNull ImportOptions options, boolean isStrictByDefault, WorkspaceFilter wspFilter, Node parent,
                                         String name, ArtifactSetImpl artifacts)
            throws RepositoryException, IOException;

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        ctx.println(isLast, getClass().getSimpleName());
    }

    protected ImportInfoImpl importDocView(InputSource source, Node parentNode, String rootNodeName, ArtifactSetImpl artifacts, WorkspaceFilter wspFilter, IdConflictPolicy idConflictPolicy) throws IOException, RepositoryException {
        return importDocView(source, parentNode, rootNodeName, artifacts, true, wspFilter, idConflictPolicy);
    }

    protected ImportInfoImpl importDocView(InputSource source, Node parentNode, String rootNodeName, ArtifactSetImpl artifacts, boolean isStrict, WorkspaceFilter wspFilter, IdConflictPolicy idConflictPolicy) throws IOException, RepositoryException {
        DocViewImporter handler = new DocViewImporter(parentNode, rootNodeName, artifacts, wspFilter, idConflictPolicy, getAcHandling(), getCugHandling(), getUncoveredAncestorHandling());
        String rootNodePath = parentNode.getPath();
        if (!rootNodePath.equals("/")) {
            rootNodePath += "/";
        }
        rootNodePath += rootNodeName;
        try {
            new DocViewParser(parentNode.getSession()).parse(rootNodePath, source, handler);
        } catch (XmlParseException e) {
            if (!isStrict) {
                // for backwards-compatibility (JCRVLT-644)
                ImportInfoImpl info = new ImportInfoImpl();
                info.onError(rootNodePath, e);
                log.error("Error while parsing {}: {}", source.getSystemId(), e);
                return info;
            } else {
                // wrap as repositoryException although not semantically correct for backwards compatibility
                throw new RepositoryException(e);
            }
        }
        return handler.getInfo();
    }
}