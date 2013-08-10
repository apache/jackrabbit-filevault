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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.AccessType;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.util.BinaryCheckOutputStream;
import org.apache.jackrabbit.vault.util.LineOutputStream;
import org.apache.jackrabbit.vault.util.MD5;

/**
 * <code>FileUtil</code>...
 *
 */
public class VaultFileCopy {

    private final VaultFile remoteFile;

    private final File localFile;

    private final MessageDigest digest;

    private byte[] lineFeed = null;

    private MD5 md5 = null;

    private long length;

    private boolean binary;

    private VaultFileCopy(VaultFile remote, File local, MessageDigest digest, byte[] lineFeed) {
        this.remoteFile = remote;
        this.localFile = local;
        this.digest = digest;
        this.lineFeed = lineFeed;
    }

    public static VaultFileCopy copy(VaultFile remote, File local) throws IOException {
        return copy(remote, local, null);
    }

    public static VaultFileCopy copy(VaultFile remote, File local, byte[] lineFeed) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("md5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e.toString());
        }
        VaultFileCopy copy = new VaultFileCopy(remote, local, md, lineFeed);
        try {
            copy.run();
        } catch (RepositoryException e) {
            throw new IOException(e.toString());
        }
        return copy;
    }

    private void run() throws IOException, RepositoryException {
        Artifact a = remoteFile.getArtifact();
        if (a.getPreferredAccess() == AccessType.NONE) {
            throw new IOException("Artifact has no content.");
        }
        OutputStream base;
        if (digest == null) {
            base = new FileOutputStream(localFile);
        } else {
            base = new DigestOutputStream(
                    new FileOutputStream(localFile), digest);
        }
        if (lineFeed != null) {
            base = new LineOutputStream(base, lineFeed);
        }
        BinaryCheckOutputStream out = new BinaryCheckOutputStream(base);
        switch (a.getPreferredAccess()) {
            case SPOOL:
                a.spool(out);
                out.close();
                break;
            case STREAM:
                InputStream in = a.getInputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                }
                in.close();
                out.close();
                break;
        }
        binary = out.isBinary();
        length = localFile.length();
        // try to set last modified
        long lastMod = remoteFile.lastModified();
        if (lastMod > 0) {
            localFile.setLastModified(lastMod);
        }
        if (digest != null) {
            md5 = new MD5(digest.digest());
        }
    }

    public MD5 getMd5() {
        return md5;
    }

    public long getLength() {
        return length;
    }

    public boolean isBinary() {
        return binary;
    }
}