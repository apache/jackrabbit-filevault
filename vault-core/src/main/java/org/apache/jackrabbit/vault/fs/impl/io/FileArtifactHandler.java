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
import java.io.InputStream;
import java.util.Calendar;
import java.util.Collection;

import javax.jcr.ImportUUIDBehavior;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.nodetype.NodeType;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.ImportArtifact;
import org.apache.jackrabbit.vault.fs.api.ImportInfo;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.ItemFilterSet;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.impl.ArtifactSetImpl;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.apache.jackrabbit.vault.util.MimeTypes;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Creates nt:file structures from  {@link SerializationType#XML_GENERIC} or
 * {@link SerializationType#GENERIC} artifacts.
 *
 */
public class FileArtifactHandler extends AbstractArtifactHandler  {

    /**
     * The node type for xml deserialization
     */
    private String xmlNodeType = "nt:xmlDocument";

    /**
     * Indicates if xml should be deserialized
     */
    private boolean explodeXml = false;

    /**
     * Returns the node type that is used for generic xml deserialization.
     * This has only an effect if {@link #isExplodeXml()} is {@code true}.
     *
     * @return the xml node type.
     */
    public String getXmlNodeType() {
        return xmlNodeType;
    }

    /**
     * Sets the node type that is used for generic xml deserialization.
     * This has only an effect if {@link #isExplodeXml()} is {@code true}.
     * <p>
     * Default is {@code nt:xmlDocument}
     *
     * @param xmlNodeType the xml node type name
     */
    public void setXmlNodeType(String xmlNodeType) {
        this.xmlNodeType = xmlNodeType;
    }

    /**
     * Checks if this handler explodes the xml for a generic xml deserialization.
     *
     * @return {@code true} if this handler explodes the xml
     */
    public boolean isExplodeXml() {
        return explodeXml;
    }

    /**
     * Sets whether this handler should explode the xml of a generic xml
     * serialization.
     * <p>
     * Default is {@code false}.
     *
     * @param explodeXml {@code true} if to explode the xml
     */
    public void setExplodeXml(boolean explodeXml) {
        this.explodeXml = explodeXml;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Handles generic artifact sets
     */
    public ImportInfoImpl accept(@NotNull ImportOptions options, WorkspaceFilter wspFilter, Node parent,
                                String name, ArtifactSetImpl artifacts)
            throws RepositoryException, IOException {
        // check if any file artifacts was removed
        ImportInfoImpl info = null;
        Collection<Artifact> removed = artifacts.removed();
        for (Artifact a: removed) {
            if (a.getType() == ArtifactType.FILE) {
                if (parent.hasNode(a.getRelativePath())) {
                    Node file = parent.getNode(a.getRelativePath());
                    String path = file.getPath();
                    // check wsp filter, only remove if 'REPLACE'
                    if (info == null) {
                        info = new ImportInfoImpl();
                    }
                    if (wspFilter.getImportMode(path) == ImportMode.REPLACE) {
                        info.onDeleted(path);
                        file.remove();
                    } else {
                        info.onNop(path);
                    }
                }
            }
        }

        // need at least a file or binary artifact
        if (artifacts.size(ArtifactType.FILE) > 0 || artifacts.size(ArtifactType.BINARY) > 0) {
            // check if the generic handler can import something
            Artifact primary = artifacts.getPrimaryData();
            if (primary != null) {
                if (info == null) {
                    info = new ImportInfoImpl();
                }
                // check import mode
                ImportMode mode = ImportMode.REPLACE;
                String path = PathUtil.getPath(parent, primary.getRelativePath());
                if (primary.getRelativePath().length() == 0 || parent.hasNode(primary.getRelativePath())) {
                    mode = wspFilter.getImportMode(path);
                }
                // only update if not MERGE (i.e. is REPLACE or UPDATE)
                // this is for maintaining backwards-compatibility the rest of the import modes are evaluated in DocViewSAXImporter
                if (mode != ImportMode.MERGE) {
                    InputSource source = primary.getInputSource();
                    if (source != null) {
                        info.merge(importDocView(parent, source, artifacts, wspFilter, options));
                    }
                } else {
                    info.onNop(path);
                }
            }
            // handle files
            for (Artifact file: artifacts.values(ArtifactType.FILE)) {
                if (info == null) {
                    info = new ImportInfoImpl();
                }
                // check type of file artifact
                if (file.getSerializationType() == SerializationType.GENERIC
                        || file.getSerializationType() == SerializationType.XML_GENERIC) {
                    // case 1: new file
                    final String fileName = file.getRelativePath();
                    if (!parent.hasNode(fileName)) {
                        importFile(info, parent, file, fileName, false);
                    } else {
                        // case 2: same structure, new data
                        if (file instanceof ImportArtifact) {
                            Node fileNode = parent.getNode(fileName);
                            // check import mode, only replace if not MERGE
                            ImportMode mode = wspFilter.getImportMode(fileNode.getPath());
                            if (mode != ImportMode.MERGE && mode != ImportMode.MERGE_PROPERTIES) {
                                if (!fileNode.hasNode(Node.JCR_CONTENT)) {
                                    // apparently no nt:file, recreate file node
                                    fileNode.remove();
                                    importFile(info, parent, file, fileName, parent.hasNode(fileName));
                                } else {
                                    Node contentNode = fileNode.getNode(Node.JCR_CONTENT);
                                    if (isModifiedNtResource(contentNode)) {
                                        contentNode.remove();
                                        contentNode = fileNode.addNode(Node.JCR_CONTENT, NodeType.NT_RESOURCE);
                                        info.onReplaced(contentNode.getPath());
                                    }
                                    if (!importNtResource(info, contentNode, file)) {
                                        info.onNop(fileNode.getPath());
                                    }
                                }
                            } else {
                                info.onNop(fileNode.getPath());
                            }
                        } else {
                            // do nothing
                        }
                    }
                } else if (file.getSerializationType() == SerializationType.XML_DOCVIEW) {
                    // special case for full coverage files below an intermediate node
                    // this is never used from {@link Importer} but only from {@link TransactionImpl}
                    String relPath = Text.getRelativeParent(file.getRelativePath(), 1);
                    String newName = Text.getName(file.getRelativePath());
                    Node newParent = parent;
                    if (relPath.length() > 0) {
                        if (parent.hasNode(relPath)) {
                            newParent = parent.getNode(relPath);
                        } else {
                            throw new IllegalArgumentException("Special docview file can't be imported. parent does not exist: " + parent.getPath() + "/" + relPath);
                        }
                    }
                    ArtifactSetImpl newSet = new ArtifactSetImpl();
                    newSet.setCoverage(ItemFilterSet.INCLUDE_ALL);

                    // check import mode
                    ImportMode mode = ImportMode.REPLACE;
                    String path = PathUtil.getPath(newParent, newName);
                    if (newName.length() == 0 || newParent.hasNode(newName)) {
                        mode = wspFilter.getImportMode(path);
                    }
                    if (mode != ImportMode.MERGE) {
                        info.merge(importDocView(file.getInputSource(), newParent, newName, newSet, wspFilter, options.getIdConflictPolicy()));
                    } else {
                        info.onNop(path);
                    }
                } else {
                    throw new IllegalArgumentException("Files of type " + file.getSerializationType() + " can't be handled by this handler " + this);
                }
            }
            ValueFactory factory = parent.getSession().getValueFactory();
            for (Artifact binary: artifacts.values(ArtifactType.BINARY)) {
                // get parent node
                Node parentNode = parent;
                String path = binary.getRelativePath();
                int idx = path.lastIndexOf('/');
                if (idx > 0) {
                    parentNode = parent.getNode(path.substring(0, idx));
                    path = path.substring(idx + 1);
                }
                // only update binary if import mode is not MERGE (because binaries have only mandatory properties)
                ImportMode mode = wspFilter.getImportMode(parentNode.getPath());
                if (mode != ImportMode.MERGE && mode != ImportMode.MERGE_PROPERTIES) {
                    Value value = factory.createValue(binary.getInputStream());
                    if (!parentNode.hasProperty(path)
                            || !value.equals(parentNode.getProperty(path).getValue())) {
                        parent.setProperty(path, value);
                        if (info == null) {
                            info = new ImportInfoImpl();
                        }
                        info.onModified(path);
                        info.onModified(parentNode.getPath());
                    }
                }
            }
        }
        return info;
    }

    /**
     * Checks if the given node is a nt_resource like structure that was modified. this is to test if a single
     * file artifact needs to recreate existing content of a sub-typed jcr:content node. see JCRVLT-177
     *
     * @param content the content node
     * @return {@code true} if modified
     * @throws RepositoryException if an error occurrs
     */
    private boolean isModifiedNtResource(Node content) throws RepositoryException {
        if (content.getMixinNodeTypes().length > 0) {
            return true;
        }
        if (content.isNodeType(NodeType.NT_RESOURCE)) {
            return false;
        }
        // allow nt:unstructured with no child nodes
        return content.hasNodes();
    }

    private void importFile(ImportInfo info, Node parent, Artifact primary, String name, boolean exists)
            throws RepositoryException, IOException {
        Node fileNode;
        Node contentNode;
        if (exists) {
            fileNode = parent.getNode(name);
            if (!fileNode.isNodeType(JcrConstants.NT_FILE)) {
                parent.getSession().refresh(false);
                throw new IOException("Incompatible content. Expected a nt:file but was " + fileNode.getPrimaryNodeType().getName());
            }
            contentNode = fileNode.getNode(JcrConstants.JCR_CONTENT);
            info.onNop(fileNode.getPath());
        } else {
            fileNode = parent.addNode(name, JcrConstants.NT_FILE);
            String contentNodeType = primary.getSerializationType() == SerializationType.XML_GENERIC
                    && isExplodeXml() ? getXmlNodeType() : JcrConstants.NT_RESOURCE;
            contentNode = fileNode.addNode(JcrConstants.JCR_CONTENT, contentNodeType);
            info.onCreated(fileNode.getPath());
            info.onCreated(contentNode.getPath());
        }
        importNtResource(info, contentNode, primary);
    }

    private ImportInfoImpl importDocView(Node parent, InputSource source,
                                     ArtifactSetImpl artifacts, WorkspaceFilter wspFilter, ImportOptions options)
            throws RepositoryException, IOException {
        String rootName = artifacts.getPrimaryData().getRelativePath();
        int idx = rootName.indexOf('/');
        if (idx > 0) {
            rootName = rootName.substring(0, idx);
        }
        return importDocView(source, parent, rootName, artifacts, wspFilter, options.getIdConflictPolicy());
    }

    private boolean importNtResource(ImportInfo info, Node content, Artifact artifact)
            throws RepositoryException, IOException {

        String path = content.getPath();
        boolean modified = false;
        if (explodeXml && !content.isNodeType(JcrConstants.NT_RESOURCE)) {
            // explode xml
            InputStream in = artifact.getInputStream();
            try {
                content.getSession().importXML(path, in, ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW);
                // can't really augment info here
            } finally {
                in.close();
            }
            modified = true;
        } else {
            ValueFactory factory = content.getSession().getValueFactory();
            Value value = factory.createValue(artifact.getInputStream());
            if (!content.hasProperty(JcrConstants.JCR_DATA)
                    || !value.equals(content.getProperty(JcrConstants.JCR_DATA).getValue())) {
                content.setProperty(JcrConstants.JCR_DATA, value);
                modified = true;
            }

            // always update last modified if binary was modified (bug #22969)
            if (!content.hasProperty(JcrConstants.JCR_LASTMODIFIED) || modified) {
                Calendar lastMod = Calendar.getInstance();
                content.setProperty(JcrConstants.JCR_LASTMODIFIED, lastMod);
                modified = true;
            }

            if (!content.hasProperty(JcrConstants.JCR_MIMETYPE)) {
                String mimeType = artifact.getContentType();
                if (mimeType == null) {
                    mimeType = Text.getName(artifact.getRelativePath(), '.');
                    mimeType = MimeTypes.getMimeType(mimeType, MimeTypes.APPLICATION_OCTET_STREAM);
                }
                content.setProperty(JcrConstants.JCR_MIMETYPE, mimeType);
                modified = true;
            }
            if (content.isNew()) {
                // mark binary data as modified
                info.onCreated(path + "/" + JcrConstants.JCR_DATA);
                info.onNop(path);
            } else if (modified) {
                // mark binary data as modified
                info.onModified(path + "/" + JcrConstants.JCR_DATA);
                info.onModified(path);
            }
        }
        return modified;
    }

}