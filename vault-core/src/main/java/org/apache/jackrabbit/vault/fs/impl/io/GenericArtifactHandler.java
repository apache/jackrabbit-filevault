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
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.impl.ArtifactSetImpl;
import org.apache.jackrabbit.vault.fs.spi.ACLManagement;
import org.apache.jackrabbit.vault.fs.spi.ServiceProviderFactory;
import org.apache.jackrabbit.vault.fs.spi.UserManagement;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Handles docview artifacts.
 *
 */
public class GenericArtifactHandler extends AbstractArtifactHandler {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(GenericArtifactHandler.class);

    private UserManagement userManagement = ServiceProviderFactory.getProvider().getUserManagement();

    private ACLManagement aclManagement = ServiceProviderFactory.getProvider().getACLManagement();

    /**
     * {@inheritDoc}
     *
     * Handles generic artifact sets
     */
    public ImportInfoImpl accept(WorkspaceFilter wspFilter, Node parent,
                                 String name, ArtifactSetImpl artifacts)
            throws RepositoryException, IOException {
        Artifact primary = artifacts.getPrimaryData();
        if (primary == null) {
            return null;
        }

        // check type of primary artifact
        ImportInfoImpl info = null;
        InputSource source = primary.getInputSource();
        if (source != null && primary.getSerializationType() == SerializationType.XML_DOCVIEW) {
            // primary docview artifact. don't except to have additional
            // extra content artifacts
            info = new ImportInfoImpl();
            String path = PathUtil.getPath(parent, name);
            if (name.length() == 0 || parent.hasNode(name)) {
                if (wspFilter.getImportMode(path) == ImportMode.MERGE) {
                    // do import the content if node is an authorizable or ACL
                    Node newNode = parent.getNode(name);
                    if (userManagement.isAuthorizableNodeType(newNode.getPrimaryNodeType().getName())) {
                        log.debug("don't skip authorizable node on MERGE: {}", path);
                    } else if (aclManagement.isACLNode(newNode)) {
                        log.debug("don't skip policy node on MERGE: {}", path);
                    } else {
                        info.onNop(path);
                        return info;
                    }
                }
            }
            try {
                DocViewSAXImporter handler = new DocViewSAXImporter(parent, name, artifacts, wspFilter);
                handler.setAclHandling(getAcHandling());
                SAXParserFactory factory = SAXParserFactory.newInstance();
                factory.setNamespaceAware(true);
                factory.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
                SAXParser parser = factory.newSAXParser();
                parser.parse(source, handler);
                info.merge(handler.getInfo());
            } catch (ParserConfigurationException e) {
                throw new RepositoryException(e);
            } catch (SAXException e) {
                info = new ImportInfoImpl();
                info.onError(path, e);
                log.error("Error while parsing {}: {}", source.getSystemId(), e);
            }
        }
        return info;
    }

}