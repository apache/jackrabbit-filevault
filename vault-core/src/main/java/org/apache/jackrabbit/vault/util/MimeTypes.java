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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/**
 * <code>MimeTypes</code> contains a mapping from extensions to mime types.
 *
 */
public class MimeTypes {

    /**
     * constant for {@value}
     */
    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

    /**
     * mapping from content-type to {@link MimeType}s.
     */
    private static final HashMap<String, MimeType> byMimeType = new HashMap<String, MimeType>();

    /**
     * mapping from extension to {@link MimeType}s.
     */
    private static final HashMap<String, MimeType> byExtension = new HashMap<String, MimeType>();

    static {
        // add some default mappings
        addMapping(false, "text/plain", "txt", "jsp", "jspx", "jspf", "ecma", "esp", "xsl", "xslt", "dtd", "properties", "tld" ,"php", "rb", "bnd");
        addMapping(false, "text/cnd"  , "cnd");
        addMapping(false, "text/x-java-source" , "java");
        addMapping(true,  "application/java-vm" , "class");
        addMapping(false, "text/html" , "html", "htm");
        addMapping(false, "text/xml"  , "xml");
        addMapping(false, "text/css"  , "css", "less");
        addMapping(false, "text/calendar", "ics");
        addMapping(false, "image/svg+xml", "svg");
        addMapping(false, "application/xliff+xml"  , "xliff");
        addMapping(true,  "image/gif" , "gif");
        addMapping(true,  "image/png" , "png");
        addMapping(true,  "image/jpeg", "jpg", "jpeg");
        addMapping(true,  "image/jpg" , "jpg", "jpeg"); // this is for compatibility reasons
        addMapping(false, "application/json", "json");
        addMapping(true,  "application/java-archive", "jar");
        addMapping(false, "application/javascript", "js");
        addMapping(false, "application/ecmascript", "ecma");
        addMapping(false, "application/x-javascript", "js"); // discouraged per RFC-4329
        addMapping(true,  "application/pdf", "pdf");
        addMapping(true,  "application/x-shockwave-flash", "swf");
        addMapping(true,  "application/zip", "zip");
        addMapping(true,  "image/vnd.microsoft.icon", "ico");
        addMapping(true,  "application/x-font-woff", "woff");
        addMapping(true,  "application/vnd.ms-fontobject", "eot");
    }

    /**
     * internally add a mapping to the static defined ones
     * @param binary binary flag
     * @param mimeType the content type
     * @param ext extensions
     */
    private static void addMapping(boolean binary, String mimeType, String ... ext) {
        if (byMimeType.containsKey(mimeType)) {
            throw new IllegalArgumentException("MimeType already registered:" + mimeType);
        }
        MimeType mt = new MimeType(mimeType, binary, ext);
        byMimeType.put(mimeType, mt);
        for (String e: ext) {
            if (!byExtension.containsKey(e)) {
                // only register default mime type
                byExtension.put(e, mt);
            }
        }
    }

    /**
     * Retrieve the mimetype for the given extension or name
     * @param name the name
     * @return the mimetype or <code>null</code>
     */
    public static String getMimeType(String name) {
        return getMimeType(name, null);
    }

    /**
     * Retrieve the mimetype for the given extension or name
     * @param name the name
     * @param defaultType type to return if no mapping is found.
     * @return the mimetype or <code>null</code>
     */
    public static String getMimeType(String name, String defaultType) {
        name = name.substring(name.lastIndexOf('.') + 1);
        MimeType mt = byExtension.get(name);
        if (mt == null) {
            return defaultType;
        } else {
            return mt.mimeType;
        }
    }

    /**
     * Retrieve the default extension for the given mime type
     * @param mimeType the mime type
     * @return the extension or null
     */
    public static String getExtension(String mimeType) {
        MimeType mt = byMimeType.get(mimeType);
        if (mt == null) {
            return null;
        } else {
            return mt.defaultExt;
        }
    }

    /**
     * checks if the given mimetype denotes binary content
     * @param mimeType the mime type
     * @return <code>true</code> if binary or if <code>mimeType</code> is <code>null</code>
     */
    public static boolean isBinary(String mimeType) {
        if (mimeType == null) {
            return true;
        }
        if (mimeType.startsWith("text/")) {
            return false;
        }
        MimeType mt = byMimeType.get(mimeType);
        return mt == null || mt.isBinary();
    }

    /**
     * Checks if the given mime type is mapped to the extension
     * @param mimeType the mime type
     * @param ext the extension
     * @return <code>true</code> if the given mime type contains that extension
     */
    public static boolean hasExtension(String mimeType, String ext) {
        MimeType mt = byMimeType.get(mimeType);
        return mt != null && mt.extensions.contains(ext);
    }

    public static boolean matches(String name, String mimeType, String defaultType) {
        name = name.substring(name.lastIndexOf('.') + 1);
        MimeType mt = byExtension.get(name);
        if (mt != null && mt.mimeType.equals(mimeType)) {
            return true;
        }
        // try reverse mapping
        mt = byMimeType.get(mimeType);
        if (mt != null && mt.extensions.contains(name)) {
            return true;
        }
        return mimeType.equals(defaultType);
    }

    /**
     * holds the mime type to extension mappings
     */
    private static class MimeType {

        /**
         * the mime type
         */
        private final String mimeType;

        /**
         * the default extension
         */
        private final String defaultExt;

        /**
         * set of all extensions
         */
        private final HashSet<String> extensions = new HashSet<String>();

        /**
         * binary flag
         */
        private final boolean binary;

        /**
         * creates a new mapping
         * @param mimeType the mime type
         * @param binary binary flag
         * @param ext the extensions
         */
        public MimeType(String mimeType, boolean binary, String ... ext) {
            this.mimeType = mimeType;
            this.binary = binary;
            this.defaultExt = ext[0];
            extensions.addAll(Arrays.asList(ext));
        }

        /**
         * Returns <code>true</code> if this is a binary mime type
         * @return <code>true</code> if binary.
         *
         */
        public boolean isBinary() {
            return binary;
        }
    }
}
