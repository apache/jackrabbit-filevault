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

package org.apache.jackrabbit.vault.fs.api;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.BitSet;

import javax.jcr.Credentials;
import javax.jcr.SimpleCredentials;

/**
 * The repository address locates a jcr repository in with a URI representation.
 * It is composed out of a uri and accepts the following formats:
 *
 * <ul>
 * <li> scheme://host:port/
 * <li> scheme://host:port/prefix
 * <li> scheme://host:port/prefix/workspace
 * <li> scheme://host:port/prefix/workspace/jcr_root/path
 * <ul>
 */
public class RepositoryAddress {

    /**
     * the (virtual) jcr root. 
     */
    public final static String JCR_ROOT = "/jcr:root";

    /**
     * the final uri
     */
    private final URI uri;

    /**
     * the specific part (uri up to excluding the workspace segment)
     */
    private final URI specific;

    /**
     * the workspace or null
     */
    private final String workspace;

    /**
     * the path
     */
    private final String path;

    /**
     * Creates a new default repository address.
     * @param uri the uri
     * @throws URISyntaxException if the uri is not valid
     */
    public RepositoryAddress(String uri) throws URISyntaxException {
        this(new URI(uri));
    }

    /**
     * Creates a new default repository address.
     * @param uri the uri
     * @throws URISyntaxException if the uri is not valid
     */
    public RepositoryAddress(URI uri) throws URISyntaxException {
        // decode uri
        String path = uri.getPath();
        String workspace;
        String prefix = "/";
        String localPath = "/";

        if (path.length() == 0 || path.equals("/")) {
            workspace = "-";
            localPath = "/";
        } else if (!uri.isAbsolute()) {
            // fix format: /wsp/path
            int idx1 = path.indexOf('/', 1);
            if (idx1 < 0) {
                workspace = path.substring(1);
            } else {
                workspace = path.substring(1, idx1);
                localPath = path.substring(idx1);
            }
        } else {
            if (path.charAt(path.length() -1) != '/') {
                path = path + "/";
            }
            int idx1 = -1;
            int idx2 = 0;
            int idx3 = path.indexOf('/', 1);
            while (idx3 > 0) {
                String segment = path.substring(idx2, idx3);
                if (segment.equals(JCR_ROOT)) {
                    break;
                }
                idx1 = idx2;
                idx2 = idx3;
                idx3 = path.indexOf('/', idx3 + 1);
            }
            if (idx3 < 0) {
                // no jcr_root found
                // special case for rmi backward compatibility
                if (uri.getScheme() != null && uri.getScheme().equals("rmi")) {
                    idx1 = path.indexOf('/', 1);
                    idx2 = path.indexOf('/', idx1 + 1);
                    if (idx2 < 0) {
                        workspace = "-";
                        prefix = path.substring(0, idx1);
                        localPath = "/";
                    } else {
                        workspace = path.substring(idx1 + 1, idx2);
                        prefix = path.substring(0, idx1);
                        int end = path.length();
                        if (end != idx2 + 1) {
                            end--;
                        }
                        localPath = path.substring(idx2, end);
                    }
                } else {
                    workspace = idx1 < 0 ? "-" : path.substring(idx1+1,idx2);
                    prefix = idx1 <= 0 ? "/" : path.substring(0, idx1);
                    localPath = "/";
                }
            } else {
                workspace = path.substring(idx1 + 1, idx2);
                prefix = path.substring(0, idx1);
                int end = path.length();
                if (end - idx3 > 1) {
                    end--;
                }
                localPath = path.substring(idx3, end);
            }
        }
        // sanitize HTTP address (probably wrong place)
        if (uri.getScheme() != null && uri.getScheme().startsWith("http")) {
            if (prefix.equals("/") || prefix.equals("/crx")) {
                prefix = "/crx/server";
                workspace = "-";
            }
        }
        if (prefix.length() == 0) {
            prefix = "/";
        }
        this.path = localPath;
        this.workspace = workspace;
        this.specific = uri.resolve(prefix);
        StringBuffer buf = new StringBuffer(specific.toString());
        if (buf.charAt(buf.length() - 1) != '/') {
            buf.append('/');
        }
        buf.append(workspace);
        buf.append(JCR_ROOT);
        if (!localPath.equals("/")) {
            buf.append(escapePath(localPath));
        }
        this.uri = new URI(buf.toString());
    }

    /**
     * Private constructor that sets all fields.
     * @param uri the address uri
     * @param specific the specific uri
     * @param workspace the workspace
     * @param path the path
     */
    private RepositoryAddress(URI uri, URI specific, String workspace, String path) {
        this.uri = uri;
        this.specific = specific;
        this.workspace = workspace;
        this.path = path;
    }


    /**
     * Returns the uri of this address
     * @return the uri of this address
     */
    public URI getURI() {
        return uri;
    }

    /**
     * Returns a new repository address with the given path.
     * @param path the path to include in the new address
     * @return a new repository address
     */
    public RepositoryAddress resolve(String path) {
        if (path == null || path.length() == 0 || path.equals(".") || path.equals("./")) {
            return this;
        }
        StringBuffer newPath = new StringBuffer(specific.getPath());
        newPath.append("/");
        newPath.append(workspace);
        newPath.append(JCR_ROOT);
        if (path.charAt(0) != '/') {
            if (this.path.endsWith("/")) {
                path = this.path + path;
            } else {
                path = this.path + "/" + path;
            }
        }
        newPath.append(escapePath(path));
        URI uri = specific.resolve(newPath.toString());
        return new RepositoryAddress(uri, specific, workspace, path);
    }

    /**
     * Returns the name of the workspace or <code>null</code> if the default
     * workspace is used.
     * @return the name of the workspace or <code>null</code>
     */
    public String getWorkspace() {
        return "-".equals(workspace) ? null : workspace;
    }

    /**
     * Returns the specific part of the uri, i.e. the part that is used to
     * actually connect to the repository
     * @return the specific part
     */
    public URI getSpecificURI() {
        return specific;
    }

    /**
     * Returns the path to a repository item. If not explicit path is specified
     * by this address the root path '/' is returned.
     * @return the path to a repository item.
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns JCR credentials from the URI or <code>null</code> if no user info
     * is specified.
     * @return the creds
     */
    public Credentials getCredentials() {
        String userinfo = uri.getUserInfo();
        if (userinfo == null) {
            return null;
        } else {
            int idx = userinfo.indexOf(':');
            if (idx < 0) {
                return new SimpleCredentials(userinfo, new char[0]);
            } else {
                return new SimpleCredentials(
                        userinfo.substring(0, idx),
                        userinfo.substring(idx+1).toCharArray());
            }
        }

    }

    /**
     * {@inheritDoc}
     *
     * @return same as {@link #getURI() getURI().toString()}
     */
    public String toString() {
        return getURI().toString();
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        return getURI().hashCode();
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof RepositoryAddress) {
            return getURI().equals(((RepositoryAddress) obj).getURI());
        }
        return false;
    }

    private static BitSet URISaveEx;

    static {
        URISaveEx = new BitSet(256);
        int i;
        for (i = 'a'; i <= 'z'; i++) {
            URISaveEx.set(i);
        }
        for (i = 'A'; i <= 'Z'; i++) {
            URISaveEx.set(i);
        }
        for (i = '0'; i <= '9'; i++) {
            URISaveEx.set(i);
        }
        URISaveEx.set('-');
        URISaveEx.set('_');
        URISaveEx.set('.');
        URISaveEx.set('!');
        URISaveEx.set('~');
        URISaveEx.set('*');
        URISaveEx.set('\'');
        URISaveEx.set('(');
        URISaveEx.set(')');
        URISaveEx.set('/');
    }

    private static final char[] hexTable = "0123456789abcdef".toCharArray();


    /**
     * Does an URL encoding of the <code>string</code> using the
     * <code>escape</code> character. The characters that don't need encoding
     * are those defined 'unreserved' in section 2.3 of the 'URI generic syntax'
     * RFC 2396, but without the escape character. If <code>isPath</code> is
     * <code>true</code>, additionally the slash '/' is ignored, too.
     *
     * @param string the string to encode.
     * @return the escaped string
     * @throws NullPointerException if <code>string</code> is <code>null</code>.
     */
    private static String escapePath(String string) {
        try {
            byte[] bytes = string.getBytes("utf-8");
            StringBuffer out = new StringBuffer(bytes.length);
            for (byte aByte : bytes) {
                int c = aByte & 0xff;
                if (URISaveEx.get(c) && c != '%') {
                    out.append((char) c);
                } else {
                    out.append('%');
                    out.append(hexTable[(c >> 4) & 0x0f]);
                    out.append(hexTable[(c) & 0x0f]);
                }
            }
            return out.toString();
        } catch (UnsupportedEncodingException e) {
            throw new InternalError(e.toString());
        }
    }


}