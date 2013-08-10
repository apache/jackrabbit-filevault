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

package org.apache.jackrabbit.vault.util;

import java.io.File;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

/**
 * <code>PathUtil</code>...
 *
 */
public class PathUtil {

    /**
     * make a canonical path. removes all /./ and /../ and multiple slashes.
     * @param parent the parent path (can be <code>null</code>)
     * @param path the path to resolve
     * @return the canonicalized path
     */
    public static String[] makePath(String[] parent, String path) {
        if (path == null || path.equals("") || path.equals(".")) {
            return parent;
        }
        // compose parent and path
        boolean isAbsolute = false;
        String[] composed = Text.explode(path, '/');
        if (path.charAt(0) == '/') {
            isAbsolute = true;
        } else {
            if (parent != null && parent.length > 0) {
                int off = 0;
                if (parent[0].equals("/")) {
                    isAbsolute = true;
                    off = 1;
                }
                String[] c = new String[parent.length - off + composed.length];
                System.arraycopy(parent, off, c, 0, parent.length - off);
                System.arraycopy(composed, 0, c, parent.length - off, composed.length);
                composed = c;
            }
        }
        // canonicalize
        int dst = 0;
        boolean startsWithParent = false;
        for (int i=0; i<composed.length; i++) {
            String element = composed[i];
            if (element.equals(".")) {
                // skip
            } else if (element.equals("..")) {
                if (dst == 0) {
                    startsWithParent = true;
                }
                if (startsWithParent) {
                    composed[dst++] = element;
                } else {
                    dst--;
                }
            } else {
                composed[dst++] = element;
            }
        }
        // truncate
        if (isAbsolute) {
            String[] ret = new String[dst + 1];
            System.arraycopy(composed, 0, ret, 1, dst);
            ret[0] = "/";
            return ret;
        } else if (dst == composed.length) {
            return composed;
        } else {
            String[] ret = new String[dst];
            System.arraycopy(composed, 0, ret, 0, dst);
            return ret;
        }
    }

    public static String makePath(String parent, String relPath) {
        String[] ret = makePath(Text.explode(parent, '/'), relPath);
        return "/" + Text.implode(ret, "/");
    }

    public static File getRelativeFile(File parent, File file) {
        return new File(getRelativeFilePath(parent.getPath(), file.getPath()));
    }
    
    public static String getRelativePath(String parent, String path) {
        return getRelativeFilePath(parent, path, "/");
    }

    public static String getRelativeFilePath(String cwd, String path) {
        return getRelativeFilePath(cwd, path, Constants.FS_NATIVE);
    }

    public static String getRelativeFilePath(String cwd, String path, String separator) {
        if (cwd.equals(path)) {
            return ".";
        }
        if (!path.startsWith(separator)) {
            // check for windows abs paths
            if (path.length() < 2 || path.charAt(1) != ':' || path.charAt(2) != '\\') {
                return path;
            }
        }
        String[] p1 = Text.explode(cwd, separator.charAt(0));
        String[] p2 = Text.explode(path, separator.charAt(0));
        // search common ancestor
        int i=0;
        while (i<p1.length && i<p2.length && p1[i].equals(p2[i])) {
            i++;
        }
        StringBuffer buf = new StringBuffer();
        String delim = "";
        // go p1.length - i levels up to the common ancestor
        for (int j = i; j<p1.length; j++) {
            buf.append(delim).append("..");
            delim = separator;
        }
        // append rest of path
        while (i<p2.length) {
            buf.append(delim).append(p2[i++]);
            delim = separator;
        }
        return buf.toString();
    }

    public static String append(String parent, String relPath) {
        if (relPath == null || relPath.length() == 0) {
            return parent == null ? "" : parent;
        }
        StringBuffer ret = new StringBuffer();
        if (parent != null) {
            ret.append(parent);
        }
        if (ret.length() > 0 && ret.charAt(ret.length()-1) != '/') {
            ret.append('/');
        }
        ret.append(relPath);
        return ret.toString();
    }
    
    public static int getDepth(String path) {
        // assume valid absolute path
        int len = path.length();
        if (len <=1) {
            return 0;
        }
        int depth = 1;
        for (int i=1; i<len; i++) {
            if (path.charAt(i) == '/') {
                depth++;
            }
        }
        return depth;
    }

    public static String getPath(Node parent, String relPath) throws RepositoryException {
        String path = parent.getPath();
        if (relPath.length() > 0) {
            if (path.endsWith("/")) {
                path += relPath;
            } else {
                path += "/" + relPath;
            }
        }
        return path;
    }
}