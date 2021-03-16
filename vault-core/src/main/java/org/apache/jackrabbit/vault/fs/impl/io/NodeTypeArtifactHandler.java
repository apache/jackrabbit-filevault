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
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.nodetype.InvalidNodeTypeDefinitionException;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeExistsException;

import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.impl.ArtifactSetImpl;
import org.apache.jackrabbit.vault.util.PathUtil;

/**
 * Creates nt:nodeType structures from {@link SerializationType#CND} artifacts.
 *
 */
public class NodeTypeArtifactHandler extends AbstractArtifactHandler {

    /**
     * {@inheritDoc}
     * <p>
     * Handles generic artifact sets
     */
    protected ImportInfoImpl accept(WorkspaceFilter wspFilter, Node parent,
                                String name, ArtifactSetImpl artifacts)
            throws RepositoryException, IOException {
        // need at least one primary data
        Artifact primary = artifacts.getPrimaryData();
        if (primary == null) {
            return null;
        }
        if (artifacts.size() != 1) {
            return null;
        }
        if (primary.getSerializationType() != SerializationType.CND) {
            return null;
        }
        ImportInfoImpl info = new ImportInfoImpl();
        String path = PathUtil.getPath(parent, primary.getRelativePath());
        if (wspFilter.getImportMode(path) == ImportMode.MERGE) {
            info.onNop(path);
            return info;
        }
        // do import
        try (InputStream in = primary.getInputStream()) {
            Reader r = new InputStreamReader(in, StandardCharsets.UTF_8);
            try {
                for (NodeType nodeType : CndImporter.registerNodeTypes(r, parent.getSession(), true)) {
                    // node types don't have a real path
                    info.onCreated(path + "/" + nodeType.getName());
                }
            } catch (ParseException e) {
                throw new RepositoryException("Invalid CND file " + name, e);
            }
        }
        return info;
    }

}