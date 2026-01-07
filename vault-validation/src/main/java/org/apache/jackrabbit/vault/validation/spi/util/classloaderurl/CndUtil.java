/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.validation.spi.util.classloaderurl;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.Manifest;

import org.osgi.annotation.versioning.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ProviderType
public final class CndUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(CndUtil.class);

    private CndUtil() {
        // static util methods only
    }
    /**
     * Resolve URLs pointing to JARs with META-INF/MANIFEST carrying a {@code Sling-Nodetypes} header
     * @param urls
     * @return
     */
    public static List<String> resolveJarUrls(List<String> urls) {
        List<String> resolvedUrls = new LinkedList<>();
        for (String url : urls) {
            url = url.trim();
            if (url.endsWith(".jar")) {
                // https://docs.oracle.com/javase/7/docs/api/java/net/JarURLConnection.html
                URL jarUrl;
                try {
                    jarUrl = URLFactory.createURL("jar:" + url + "!/");
                    JarURLConnection jarConnection = (JarURLConnection) jarUrl.openConnection();
                    Manifest manifest = jarConnection.getManifest();
                    String slingNodetypes = manifest.getMainAttributes().getValue("Sling-Nodetypes");
                    // split by "," and generate new JAR Urls
                    if (slingNodetypes == null) {
                        LOGGER.warn("No 'Sling-Nodetypes' header found in manifest of '{}'", jarUrl);
                    } else {
                        for (String nodetype : slingNodetypes.split(",")) {
                            resolvedUrls.add(jarUrl.toString() + nodetype.trim());
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalArgumentException("Could not read from JAR " + url, e);
                }
            } else {
                resolvedUrls.add(url);
            }
        }
        return resolvedUrls;
    }
}
