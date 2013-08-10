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

package org.apache.jackrabbit.vault.fs.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;

/**
 * Implements a generic abstract artifact
 *
 */
public abstract class AbstractArtifact implements Artifact {

    /**
     * the repository relative path of this artifact
     */
    private final String repoRelPath;

    /**
     * the platform relative path or <code>null</code> if not differs from
     * {@link #repoRelPath}.
     */
    private String platRelPath;

    /**
     * the extension
     */
    private final String extension;

    /**
     * the artifact type
     */
    private final ArtifactType type;

    /**
     * the content type
     */
    private String contentType;

    /**
     * Creates a new abstract artifact with the given repository name,
     * platform extension and and type. the parent artifact is only used for
     * generating the paths and is not linked to this artifact.
     *
     * @param parent the parent artifact or <code>null</code>.
     * @param repoRelPath the repository name
     * @param extension the platform extension
     * @param type type of the artifact
     */
    protected AbstractArtifact(Artifact parent, String repoRelPath,
                               String extension, ArtifactType type) {
        this.type = type;
        this.extension = extension;
        if (parent == null) {
            this.repoRelPath = repoRelPath;
            this.platRelPath = PlatformNameFormat.getPlatformPath(repoRelPath + extension);
        } else {
            this.repoRelPath = PathUtil.append(parent.getRelativePath(), repoRelPath);
            this.platRelPath = PathUtil.append(parent.getPlatformPath(), PlatformNameFormat.getPlatformPath(repoRelPath + extension));
        }
        if (this.platRelPath.equals(this.repoRelPath)) {
            this.platRelPath = null;
        }
    }

    /**
     * Creates a new abstract artifact initialized with the values from the
     * given one.
     * @param base base artifact
     * @param type the new type
     */
    protected AbstractArtifact(Artifact base, ArtifactType type) {
        this.type = type;
        this.extension = base.getExtension();
        this.repoRelPath = base.getRelativePath();
        this.platRelPath = base.getPlatformPath();
        this.contentType = base.getContentType();
    }

    /**
     * {@inheritDoc}
     */
    public String getRelativePath() {
        return repoRelPath;
    }

    /**
     * {@inheritDoc}
     */
    public String getPlatformPath() {
        return platRelPath == null
                ? repoRelPath
                : platRelPath;
    }

    /**
     * {@inheritDoc}
     */
    public String getExtension() {
        return extension;
    }

    /**
     * {@inheritDoc}
     */
    public ArtifactType getType() {
        return type;
    }

    /**
     * {@inheritDoc}
     *
     * Provides a generic spool mechanism from the {@link #getInputStream()}
     * to the provided output stream. 
     */
    public void spool(OutputStream out)
            throws IOException, RepositoryException {
        InputStream in = getInputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        in.close();
        out.close();
    }

    /**
     * {@inheritDoc}
     *
     * @return the final name and the type
     */
    public String toString() {
        StringBuffer buf = new StringBuffer(type.toString());
        buf.append('(').append(repoRelPath).append(')');
        return buf.toString();
    }

    /**
     * {@inheritDoc}
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Sets the content type
     * @param contentType the content type
     */
    protected void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        return repoRelPath.hashCode() + 37 * type.hashCode();
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        else if (obj instanceof Artifact) {
            Artifact a = (Artifact) obj;
            return getRelativePath().equals(a.getRelativePath()) && type == a.getType();
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        ctx.println(isLast, "Artifact");
        ctx.indent(isLast);
        ctx.printf(false, "rel path: %s", getRelativePath());
        ctx.printf(false, "plt path: %s", getPlatformPath());
        ctx.printf(false, "type: %s", getType());
        ctx.printf(false, "serialization: %s", getSerializationType());
        ctx.printf(false, "access type: %s", getPreferredAccess());
        ctx.printf(false, "content type: %s", getContentType());
        ctx.printf(true, "content length: %d", getContentLength());
        ctx.outdent();
    }

}
