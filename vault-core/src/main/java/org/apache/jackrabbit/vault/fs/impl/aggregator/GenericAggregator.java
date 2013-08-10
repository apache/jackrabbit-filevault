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

package org.apache.jackrabbit.vault.fs.impl.aggregator;

import java.util.Collection;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.DirectoryArtifact;
import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.apache.jackrabbit.vault.fs.api.Aggregator;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.ArtifactSet;
import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.Dumpable;
import org.apache.jackrabbit.vault.fs.api.ImportInfo;
import org.apache.jackrabbit.vault.fs.api.ItemFilterSet;
import org.apache.jackrabbit.vault.fs.impl.ArtifactSetImpl;
import org.apache.jackrabbit.vault.fs.impl.io.DocViewSerializer;
import org.apache.jackrabbit.vault.fs.impl.io.ImportInfoImpl;
import org.apache.jackrabbit.vault.fs.io.Serializer;
import org.apache.jackrabbit.vault.fs.spi.ACLManagement;
import org.apache.jackrabbit.vault.fs.spi.ServiceProviderFactory;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.PathUtil;

/**
 */
public class GenericAggregator implements Aggregator, Dumpable {

    /**
     * name hint for the aggregator
     */
    private String name;

    /**
     * flag indicating if this aggregator aggregates children
     */
    private boolean fullCoverage = false;

    /**
     * filter that regulates what to be included in the aggregate
     */
    private final ItemFilterSet contentFilter = new ItemFilterSet();

    /**
     * filter that regulates if this aggregator is to be used
     */
    private final ItemFilterSet matchFilter = new ItemFilterSet();

    /**
     * the 'default' aggregator flag
     */
    private boolean isDefault = false;

    /**
     * acl management
     */
    private ACLManagement aclManagement;

    private ACLManagement getAclManagement() {
        if (aclManagement == null) {
            aclManagement = ServiceProviderFactory.getProvider().getACLManagement();
        }
        return aclManagement;
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasFullCoverage() {
        return fullCoverage;
    }

    public void setHasFullCoverage(String hasFullCoverage) {
        this.fullCoverage = Boolean.valueOf(hasFullCoverage);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isDefault() {
        return isDefault;
    }

    /**
     * Sets the default flag
     * @param aDefault the default flag
     */
    public void setIsDefault(String aDefault) {
        isDefault = Boolean.valueOf(aDefault);
    }

    /**
     * Returns the name of this aggregator
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this aggregator
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }


    /**
     * Sets the flag indicating if this aggregator aggregates children
     *
     * @param fullCoverage <code>true</code> if this aggregator aggregates
     *        children
     */
    public void setFullCoverage(String fullCoverage) {
        this.fullCoverage = Boolean.valueOf(fullCoverage);
    }


    /**
     * Returns the content filter
     * @return the content filter
     */
    public ItemFilterSet getContentFilter() {
        return contentFilter;
    }

    /**
     * Returns the match filter
     * @return the match filter
     */
    public ItemFilterSet getMatchFilter() {
        return matchFilter;
    }

    /**
     * {@inheritDoc}
     */
    public boolean includes(Node root, Node node, String path) throws RepositoryException {
        if (path == null) {
            path = node.getPath();
        }
        return contentFilter.contains(node, path, PathUtil.getDepth(path) - root.getDepth());
    }

    /**
     * {@inheritDoc}
     */
    public boolean matches(Node node, String path) throws RepositoryException {
        return matchFilter.contains(node, path, 0);
    }

    /**
     * {@inheritDoc}
     */
    public boolean includes(Node root, Node parent, Property property, String path) throws RepositoryException {
        if (path == null) {
            path = property.getPath();
        }
        return contentFilter.contains(property, path, PathUtil.getDepth(path) - root.getDepth());
    }

    /**
     * {@inheritDoc}
     * @param aggregate
     */
    public ArtifactSet createArtifacts(Aggregate aggregate) throws RepositoryException {
        ArtifactSetImpl artifacts = new ArtifactSetImpl();

        // set coverage filter to artifacts
        artifacts.setCoverage(getContentFilter());

        // add directory
        ArtifactType type = ArtifactType.PRIMARY;
        String name = aggregate.getName();
        String ext = ".xml";
        Artifact parent = null;
        if (!hasFullCoverage()) {
            parent = new DirectoryArtifact(name);
            artifacts.add(parent);
            name = "";
            ext = Constants.DOT_CONTENT_XML;
            // special optimization if only nt:folder
            if (aggregate.getNode().getPrimaryNodeType().getName().equals("nt:folder")
                    && aggregate.getNode().getMixinNodeTypes().length == 0) {
                return artifacts;
            }
        }

        // add extra
        Serializer ser = new DocViewSerializer(aggregate);
        artifacts.add(parent, name, ext, type, ser, 0);

        // add binaries
        Collection<Property> bins = aggregate.getBinaries();
        if (bins != null && !bins.isEmpty()) {
            if (hasFullCoverage()) {
                assert parent == null;
                parent = new DirectoryArtifact(aggregate.getName());
                artifacts.add(parent);
            }
            int pathLen = aggregate.getPath().length();
            if (pathLen > 1) {
                pathLen++;
            }
            for (Property prop: bins) {
                String relPath = prop.getPath().substring(pathLen);
                artifacts.add(parent, relPath, ".binary", ArtifactType.BINARY, prop, 0);
            }

        }
        return artifacts;
    }

    /**
     * {@inheritDoc}
     *
     * Throws an exception if this aggregator allows children but
     * <code>recursive</code> is <code>false</code>.
     */
    public ImportInfo remove(Node node, boolean recursive, boolean trySave)
            throws RepositoryException {
        if (fullCoverage && !recursive) {
            // todo: allow smarter removal
            throw new RepositoryException("Unable to remove content since aggregation has children and recursive is not set.");
        }
        ImportInfo info = new ImportInfoImpl();
        info.onDeleted(node.getPath());
        Node parent = node.getParent();
        if (getAclManagement().isACLNode(node)) {
            getAclManagement().clearACL(parent);
        } else {
            node.remove();
        }
        if (trySave) {
            parent.save();
        }
        return info;
    }

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        ctx.println(isLast, getClass().getSimpleName());
        ctx.indent(isLast);
        ctx.printf(false, "name: %s", getName());
        ctx.printf(false, "fullCoverage: %b", hasFullCoverage());
        ctx.printf(false, "default: %b", isDefault());
        ctx.println(false, "Content Filter");
        ctx.indent(false);
        getContentFilter().dump(ctx, true);
        ctx.outdent();
        ctx.println(true, "Match Filter");
        ctx.indent(true);
        getMatchFilter().dump(ctx, true);
        ctx.outdent();
        ctx.outdent();
    }

}