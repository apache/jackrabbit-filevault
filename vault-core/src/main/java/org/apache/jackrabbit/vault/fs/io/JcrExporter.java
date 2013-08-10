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

package org.apache.jackrabbit.vault.fs.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.jackrabbit.vault.util.Text;

/**
 * Implements a Vault filesystem exporter that exports Vault files to a JCR
 * repository.
 * It uses the {@link PlatformNameFormat} for formatting the jcr file
 * names to local ones.
 */
public class JcrExporter extends AbstractExporter {

    private final Node localParent;

    private boolean autoDeleteFiles;

    /**
     * Constructs a new jcr exporter.
     * @param localFile the local parent folder
     */
    public JcrExporter(Node localFile) {
        this.localParent = localFile;
    }

    public boolean isAutoDeleteFiles() {
        return autoDeleteFiles;
    }

    public void setAutoDeleteFiles(boolean autoDeleteFiles) {
        this.autoDeleteFiles = autoDeleteFiles;
    }

    /**
     * {@inheritDoc}
     */
    public void open() throws IOException, RepositoryException {
        scan(localParent);
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException, RepositoryException {
        if (autoDeleteFiles) {
            for (ExportInfo.Entry e: exportInfo.getEntries().values()) {
                if (e.type == ExportInfo.Type.DELETE) {
                    String relPath = PathUtil.getRelativePath(localParent.getPath(), e.path);
                    try {
                        Node node = localParent.getNode(relPath);
                        node.remove();
                        track("D", relPath);
                    } catch (RepositoryException e1) {
                        track(e1, relPath);
                    }
                }
            }
        }
        localParent.save();
    }

    private void scan(Node dir) throws RepositoryException {
        NodeIterator iter = dir.getNodes();
        while (iter.hasNext()) {
            Node child = iter.nextNode();
            String name = child.getName();
            if (name.equals(".svn") || name.equals(".vlt")) {
                continue;
            }
            if (child.isNodeType(JcrConstants.NT_FOLDER)) {
                exportInfo.update(ExportInfo.Type.RMDIR, child.getPath());
                scan(child);
            } else if (child.isNodeType(JcrConstants.NT_FILE)) {
                exportInfo.update(ExportInfo.Type.DELETE, child.getPath());
            }
        }
    }

    public void createDirectory(VaultFile file, String relPath)
            throws RepositoryException, IOException {
        getOrCreateItem(getPlatformFilePath(file, relPath), true);
    }

    public void createDirectory(String relPath) throws IOException {
        getOrCreateItem(relPath, true);
    }

    public void writeFile(VaultFile file, String relPath)
            throws RepositoryException, IOException {
        Node local = getOrCreateItem(getPlatformFilePath(file, relPath), false);
        track(local.isNew() ? "A" : "U", relPath);
        Node content;
        if (local.hasNode(JcrConstants.JCR_CONTENT)) {
            content = local.getNode(JcrConstants.JCR_CONTENT);
        } else {
            content = local.addNode(JcrConstants.JCR_CONTENT, JcrConstants.NT_RESOURCE);
        }
        Artifact a = file.getArtifact();
        switch (a.getPreferredAccess()) {
            case NONE:
                throw new RepositoryException("Artifact has no content.");

            case SPOOL:
                // we can't support spool
            case STREAM:
                InputStream in = a.getInputStream();
                Binary b = content.getSession().getValueFactory().createBinary(in);
                content.setProperty(JcrConstants.JCR_DATA, b);
                b.dispose();
                in.close();
                break;
        }
        Calendar now = Calendar.getInstance();
        if (a.getLastModified() >= 0) {
            now.setTimeInMillis(a.getLastModified());
        }
        content.setProperty(JcrConstants.JCR_LASTMODIFIED, now);
        if (a.getContentType() != null) {
            content.setProperty(JcrConstants.JCR_MIMETYPE, a.getContentType());
        } else if (!content.hasProperty(JcrConstants.JCR_MIMETYPE)){
            content.setProperty(JcrConstants.JCR_MIMETYPE, "application/octet-stream");
        }
    }

    public void writeFile(InputStream in, String relPath) throws IOException {
        try {
            Node content;
            Node local = getOrCreateItem(relPath, false);
            if (local.hasNode(JcrConstants.JCR_CONTENT)) {
                content = local.getNode(JcrConstants.JCR_CONTENT);
            } else {
                content = local.addNode(JcrConstants.JCR_CONTENT, JcrConstants.NT_RESOURCE);
            }
            Binary b = content.getSession().getValueFactory().createBinary(in);
            content.setProperty(JcrConstants.JCR_DATA, b);
            content.setProperty(JcrConstants.JCR_LASTMODIFIED, Calendar.getInstance());
            if (!content.hasProperty(JcrConstants.JCR_MIMETYPE)){
                content.setProperty(JcrConstants.JCR_MIMETYPE, "application/octet-stream");
            }
            b.dispose();
            in.close();
        } catch (RepositoryException e) {
            IOException io = new IOException("Error while writing file " + relPath);
            io.initCause(e);
            throw io;
        }
    }

    private Node getOrCreateItem(String relPath, boolean isDir) throws IOException {
        try {
            String[] segments = Text.explode(relPath, '/');
            Node root = localParent;
            for (int i=0; i<segments.length; i++) {
                String s = segments[i];
                if (root.hasNode(s)) {
                    root = root.getNode(s);
                    if (isDir) {
                        exportInfo.update(ExportInfo.Type.NOP, root.getPath());
                    } else {
                        exportInfo.update(ExportInfo.Type.UPDATE, root.getPath());
                    }
                } else {
                    if (i == segments.length -1 && !isDir) {
                        root = root.addNode(s, JcrConstants.NT_FILE);
                        exportInfo.update(ExportInfo.Type.ADD, root.getPath());
                    } else {
                        root = root.addNode(s, JcrConstants.NT_FOLDER);
                        exportInfo.update(ExportInfo.Type.MKDIR, root.getPath());
                    }
                }
            }
            return root;
        } catch (RepositoryException e) {
            IOException io = new IOException("Error while creating item " + relPath);
            io.initCause(e);
            throw io;
        }
    }

}