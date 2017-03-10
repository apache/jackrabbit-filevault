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

package org.apache.jackrabbit.vault.fs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedList;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.AccessType;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.ExportArtifact;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.impl.AbstractArtifact;
import org.apache.jackrabbit.vault.util.JcrConstants;

/**
 * Implements a artifact that is based on a property value.
 *
 */
public class PropertyValueArtifact extends AbstractArtifact implements ExportArtifact {
    /**
     * The property of this artifact
     */
    private final Property property;

    /**
     * the path to the property
     */
    private final String path;

    /**
     * Temporary file if the value is detached
     */
    private File tmpFile;

    /**
     * cached content length
     */
    private Long contentLength;

    private final long lastModified;

    /**
     * The value index for multi value properties.
     */
    private final int valueIndex;

    public PropertyValueArtifact(Artifact parent, String relPath, String ext, ArtifactType type,
                                 Property prop, long lastModified)
            throws RepositoryException {
        this(parent, relPath, ext, type, prop, -1, lastModified);
    }

    public PropertyValueArtifact(Artifact parent, String relPath, String ext, ArtifactType type,
                                 Property prop, int index, long lastModified)
            throws RepositoryException {
        super(parent, relPath, ext, type);
        this.property = prop;
        this.path = prop.getPath();
        if (prop.getDefinition().isMultiple()) {
            if (index < 0) {
                index = 0;
            }
        } else {
            if (index >=0) {
                index = -1;
            }
        }
        this.valueIndex = index;
        this.lastModified = lastModified;
    }

    /**
     * Creates a collection of {@link PropertyValueArtifact} from the given
     * property. If the property is multivalued there will be an artifact
     * created for each value with the value index appended to it's name.
     *
     * @param parent parent artifact
     * @param relPath the base name for the artifact(s).
     * @param ext the extension
     * @param type the type for the artifact(s).
     * @param prop the property for the artifact(s).
     * @param lastModified the last modified date.
     *
     * @return a collection of Artifacts.
     * @throws RepositoryException if an error occurs
     */
    public static Collection<PropertyValueArtifact> create(Artifact parent,
                   String relPath, String ext, ArtifactType type, Property prop, long lastModified)
            throws RepositoryException {
        LinkedList<PropertyValueArtifact> list = new LinkedList<PropertyValueArtifact>();
        if (prop.getDefinition().isMultiple()) {
            Value[] values = prop.getValues();
            for (int i=0; i<values.length; i++) {
                StringBuffer n = new StringBuffer(relPath);
                n.append('[').append(i).append(']');
                list.add(new PropertyValueArtifact(parent, n.toString(), ext, type, prop, i, lastModified));
            }
        } else {
            list.add(new PropertyValueArtifact(parent, relPath, ext, type, prop, lastModified));
        }
        return list;
    }

    /**
     * {@inheritDoc}
     */
    public SerializationType getSerializationType() {
        return SerializationType.GENERIC;
    }

    /**
     * {@inheritDoc}
     *
     * @return always {@link AccessType#STREAM}
     */
    public AccessType getPreferredAccess() {
        return AccessType.STREAM;
    }

    /**
     * {@inheritDoc}
     */
    public InputStream getInputStream() throws IOException, RepositoryException {
        return tmpFile == null ?  new PVAInputStream() : new FileInputStream(tmpFile);
    }

    /**
     * Detaches the value from the underlying property value.
     *
     * @throws IOException if an I/O error occurs
     * @throws RepositoryException if a repository error occurs.
     */
    public void detach() throws IOException, RepositoryException {
        if (tmpFile == null) {
            // ensure caching of content type
            getContentType();
            // copy value to temp file
            tmpFile = File.createTempFile("jcrfs", "dat");
            tmpFile.setLastModified(getLastModified());
            tmpFile.deleteOnExit();
            FileOutputStream out = new FileOutputStream(tmpFile);
            InputStream in = getValue().getStream();
            IOUtils.copy(in, out);
            in.close();
            out.close();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return a input source which systemId is the path of the underlying property
     */
    public VaultInputSource getInputSource() throws IOException, RepositoryException {
        final InputStream in = getInputStream();
        return new VaultInputSource() {

            @Override
            public String getSystemId() {
                return path;
            }

            @Override
            public InputStream getByteStream() {
                return in;
            }


            public long getContentLength() {
                return PropertyValueArtifact.this.getContentLength();
            }

            public long getLastModified() {
                return PropertyValueArtifact.this.getLastModified();
            }
        };
    }

    /**
     * Returns the value either from field or from property.
     *
     * @return the jcr value.
     * @throws RepositoryException if an repository error occurs.
     */
    private Value getValue() throws RepositoryException {
        if (valueIndex < 0) {
            return property.getValue();
        } else {
            Value[] values = property.getValues();
            if (valueIndex >= values.length) {
                throw new RepositoryException("Illegal value index: " + valueIndex);
            }
            return values[valueIndex];
        }
    }

    /**
     * {@inheritDoc}
     */
    public long getContentLength() {
        if (contentLength == null) {
            if (tmpFile == null) {
                contentLength = -1L;
                try {
                    if (valueIndex < 0) {
                        contentLength = property.getLength();
                    } else {
                        long[] lengths = property.getLengths();
                        if (valueIndex < lengths.length) {
                            contentLength = lengths[valueIndex];
                        }
                    }
                } catch (RepositoryException e) {
                    // ignore
                }
            } else {
                contentLength = tmpFile.length();
            }
        }
        return contentLength;
    }

    /**
     * Returns the underlying property
     * @return the underlying property
     */
    public Property getProperty() {
        return property;
    }

    /**
     * {@inheritDoc}
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * {@inheritDoc}
     */
    public String getContentType() {
        String ct = super.getContentType();
        if (ct == null && tmpFile == null) {
            try {
                Node parent = property.getParent();
                if (parent.hasProperty(JcrConstants.JCR_MIMETYPE)) {
                    ct = parent.getProperty(JcrConstants.JCR_MIMETYPE).getString();
                }
            } catch (RepositoryException e) {
                // ignore
            }
            super.setContentType(ct);
        }
        return ct;
    }

    /**
     * Internal defered input stream on this property value
     */
    private class PVAInputStream extends InputStream {

        private InputStream stream;

        private boolean closed;

        private void assertOpen() throws IOException {
            if (stream == null) {
                if (closed) {
                    throw new IOException("Stream already closed.");
                }
                try {
                    stream = getValue().getStream();
                } catch (RepositoryException e) {
                    throw new IOException("Error while opening stream: " + e.toString());
                }
            }
        }
        public int read() throws IOException {
            assertOpen();
            return stream.read();
        }

        public int read(byte[] b) throws IOException {
            assertOpen();
            return stream.read(b);
        }

        public int read(byte[] b, int off, int len) throws IOException {
            assertOpen();
            return stream.read(b, off, len);
        }

        public long skip(long n) throws IOException {
            assertOpen();
            return stream.skip(n);
        }

        public int available() throws IOException {
            assertOpen();
            return stream.available();
        }

        public void close() throws IOException {
            try {
                if (stream != null) {
                    stream.close();
                }
            } finally {
                closed = true;
                stream = null;
            }
        }

        public void mark(int readlimit) {
            try {
                assertOpen();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            stream.mark(readlimit);
        }

        public void reset() throws IOException {
            assertOpen();
            stream.reset();
        }

        public boolean markSupported() {
            try {
                assertOpen();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return stream.markSupported();
        }
    }

}