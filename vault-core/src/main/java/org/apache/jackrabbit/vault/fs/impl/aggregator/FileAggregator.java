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

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

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
import org.apache.jackrabbit.vault.fs.impl.AggregateManagerImpl;
import org.apache.jackrabbit.vault.fs.impl.ArtifactSetImpl;
import org.apache.jackrabbit.vault.fs.impl.io.DocViewSerializer;
import org.apache.jackrabbit.vault.fs.impl.io.ImportInfoImpl;
import org.apache.jackrabbit.vault.fs.io.Serializer;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.apache.jackrabbit.vault.util.MimeTypes;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.apache.jackrabbit.vault.util.Text;

/**
 * A file aggregate contains nt:file or nt:resource nodes.
 * It always produces a primary FileArtifact that represents the jcr:data,
 * and jcr:lastModified. if the jcr:mimeType is correctly detectable from the
 * file's extension it's also included in that artifact.
 * if the mapping is ambiguous or if the file or content contains additional
 * information, an additional directory artifact ".dir" and a .content.xml
 * is created.
 */
public class FileAggregator implements Aggregator, Dumpable {

    /**
     * filter that regulates what to be included in the aggregate
     */
    private final ItemFilterSet contentFilter = new ItemFilterSet();

    /**
     * {@inheritDoc}
     */
    public boolean matches(Node node, String path) throws RepositoryException {
        if (node.isNodeType(JcrConstants.NT_FILE)) {
            return node.hasProperty(JcrConstants.JCR_CONTENT + "/" + JcrConstants.JCR_DATA);
        } else {
            return node.isNodeType(JcrConstants.NT_RESOURCE);
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean includes(Node root, Node node, Property prop, String path)
            throws RepositoryException {
        // we reject auto-generated properties so that they don't get
        // included in the .dir/.content.xml
        if (node.getName().equals(JcrConstants.JCR_CONTENT)) {
            String name = path == null ? prop.getName() : Text.getName(path);
            if (name.equals(JcrConstants.JCR_DATA)) {
                return false;
            } else if (name.equals(JcrConstants.JCR_LASTMODIFIED)) {
                return false;
            } else if (name.equals(JcrConstants.JCR_UUID)) {
                return false;
            } else if (name.equals(JcrConstants.JCR_MIMETYPE)) {
                String expected = MimeTypes.getMimeType(root.getName(), MimeTypes.APPLICATION_OCTET_STREAM);
                return !expected.equals(prop.getString());
            } else if (name.equals(JcrConstants.JCR_ENCODING)) {
                if (prop.getString().equals("utf-8")) {
                    String mimeType = MimeTypes.getMimeType(root.getName(), MimeTypes.APPLICATION_OCTET_STREAM);
                    return MimeTypes.isBinary(mimeType);
                }
            }
        } else if (node == root && node.isNodeType(JcrConstants.NT_RESOURCE)) {
            // for nt:resource nodes, we only reject data and last modified
            // we don't want to risk to the entire node gets rejected.
            String name = path == null ? prop.getName() : Text.getName(path);
            if (name.equals(JcrConstants.JCR_DATA)) {
                return false;
            } else if (name.equals(JcrConstants.JCR_LASTMODIFIED)) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public boolean includes(Node root, Node node, String path) throws RepositoryException {
        if (path == null) {
            path = node.getPath();
        }
        int depth = PathUtil.getDepth(path) - root.getDepth();
        boolean isFile = root.isNodeType(JcrConstants.NT_FILE);
        if (depth == 0) {
            // should not happen, but ok.
            return true;
        } else if (depth == 1) {
            // on root level, we only allow "jcr:content" if it's a file
            if (isFile && node.getName().equals(JcrConstants.JCR_CONTENT)) {
                return true;
            }
        } else {
            // no sub nodes.
        }
        // only respect content filter if not empty
        return !contentFilter.isEmpty() && contentFilter.contains(node, path, depth);
    }

    /**
     * {@inheritDoc}
     *
     * @return <code>false</code> always.
     */
    public boolean hasFullCoverage() {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @return <code>false</code> always.
     */
    public boolean isDefault() {
        return false;
    }

    /**
     * {@inheritDoc}
     * @param aggregate
     */
    public ArtifactSet createArtifacts(Aggregate aggregate) throws RepositoryException {
        ArtifactSetImpl artifacts = new ArtifactSetImpl();
        Node node = aggregate.getNode();
        ((AggregateManagerImpl) aggregate.getManager()).addNodeTypes(node);
        Node content = node;
        if (content.isNodeType(JcrConstants.NT_FILE)) {
            content = node.getNode(JcrConstants.JCR_CONTENT);
            ((AggregateManagerImpl) aggregate.getManager()).addNodeTypes(content);
        }
        // retrieve basic properties
        long lastModified = 0;
        String encoding = null;
        try {
            lastModified = content.getProperty(JcrConstants.JCR_LASTMODIFIED).getDate().getTimeInMillis();
        } catch (RepositoryException e) {
            // ignore
        }
        String mimeType = null;
        if (content.hasProperty(JcrConstants.JCR_MIMETYPE)) {
            try {
                mimeType = content.getProperty(JcrConstants.JCR_MIMETYPE).getString();
            } catch (RepositoryException e) {
                // ignore
            }
        }
        if (mimeType == null) {
            // guess mime type from name
            mimeType = MimeTypes.getMimeType(node.getName(), MimeTypes.APPLICATION_OCTET_STREAM);
        }

        if (content.hasProperty(JcrConstants.JCR_ENCODING)) {
            try {
                encoding = content.getProperty(JcrConstants.JCR_ENCODING).getString();
            } catch (RepositoryException e) {
                // ignore
            }
        }
        // check needs .dir artifact nt:file. we trust that the repository does
        // not add other properties than the one defined in JCR.
        boolean needsDir = !node.getPrimaryNodeType().getName().equals(JcrConstants.NT_FILE);
        if (!needsDir) {
            // suppress mix:lockable (todo: make configurable)
            if (node.hasProperty(JcrConstants.JCR_MIXINTYPES)) {
                for (Value v: node.getProperty(JcrConstants.JCR_MIXINTYPES).getValues()) {
                    if (!v.getString().equals(JcrConstants.MIX_LOCKABLE)) {
                        needsDir = true;
                        break;
                    }
                }
            }
        }
        if (!needsDir) {
            needsDir = !content.getPrimaryNodeType().getName().equals(JcrConstants.NT_RESOURCE);
        }
        if (!needsDir) {
            if (content.hasProperty(JcrConstants.JCR_MIXINTYPES)) {
                for (Value v: content.getProperty(JcrConstants.JCR_MIXINTYPES).getValues()) {
                    if (!v.getString().equals(JcrConstants.MIX_LOCKABLE)) {
                        needsDir = true;
                        break;
                    }
                }
            }
        }
        if (!needsDir) {
            needsDir = !MimeTypes.matches(node.getName(), mimeType, MimeTypes.APPLICATION_OCTET_STREAM);
        }
        if (!needsDir && encoding != null) {
            needsDir = !"utf-8".equals(encoding) || MimeTypes.isBinary(mimeType);
        }

        // create file artifact
        String name = aggregate.getName();
        artifacts.add(null, name, "", ArtifactType.FILE,
                content.getProperty(JcrConstants.JCR_DATA), lastModified);

        // create .dir artifact
        if (needsDir) {
            // in this case, we create a directory artifact
            Artifact parent = new DirectoryArtifact(name, ".dir");
            artifacts.add(parent);
            // and extra
            Serializer ser = new DocViewSerializer(aggregate);
            // hack: do better
            artifacts.add(parent, "", Constants.DOT_CONTENT_XML, ArtifactType.PRIMARY, ser, 0);
        }
        return artifacts;
    }

    /**
     * {@inheritDoc}
     */
    public ImportInfo remove(Node node, boolean recursive, boolean trySave)
            throws RepositoryException {
        ImportInfo info = new ImportInfoImpl();
        info.onDeleted(node.getPath());
        Node parent = node.getParent();
        node.remove();
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
    }

}