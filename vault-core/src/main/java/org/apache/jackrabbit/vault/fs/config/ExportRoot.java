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

package org.apache.jackrabbit.vault.fs.config;

import java.io.File;
import java.io.IOException;

import org.apache.jackrabbit.vault.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the root of a vault export or a vlt checkout. it has the following
 * structure:
 *
 * <xmp>
 * root
 * |-- META-INF
 * |   `-- vault
 * |       |-- config.xml
 * |       |-- filter.xml
 * |       |-- nodetypes.cnd
 * |       `-- properties.xml
 * `-- jcr_root
 *     :
 * </xmp>
 *
 */
public class ExportRoot {

    private static final Logger log = LoggerFactory.getLogger(ExportRoot.class);

    private final File rootDir;

    private final File jcrRoot;

    private final File metaDir;

    private DefaultMetaInf metaInf;

    public ExportRoot(File rootDir) {
        this.rootDir = rootDir;
        this.jcrRoot = new File(rootDir, Constants.ROOT_DIR);
        this.metaDir = new File(rootDir, Constants.META_DIR);
    }

    /**
     * Checks if this export root already has the necessary structure setup.
     * @return <code>true</code> if valid.
     */
    public boolean isValid() {
        return jcrRoot.isDirectory() && metaDir.isDirectory();
    }

    public void assertValid() throws IOException {
        if (!isValid()) {
            throw new IOException(Constants.META_DIR + " does not exist or is not valid.");
        }
    }

    /**
     * Creates the necessary directories if they do not exist yet.
     * @throws IOException if an I/O error occurs
     */
    public void create() throws IOException {
        if (!jcrRoot.isDirectory()) {
            jcrRoot.mkdirs();
            if (!jcrRoot.isDirectory()) {
                throw new IOException("Unable to create " + jcrRoot.getAbsolutePath());
            }
            log.info("Created {}", jcrRoot.getAbsolutePath());
        }
        if (!metaDir.isDirectory()) {
            metaDir.mkdirs();
            if (!metaDir.isDirectory()) {
                throw new IOException("Unable to create " + metaDir.getAbsolutePath());
            }
            log.info("Created {}", metaDir.getAbsolutePath());
        }
    }

    /**
     * Returns the meta information.
     * @return the meta information.
     */
    public MetaInf getMetaInf() {
        if (metaInf == null) {
            metaInf = new DefaultMetaInf();
            if (metaDir.exists()) {
                try {
                    metaInf.loadConfig(metaDir);
                    metaInf.loadFilter(metaDir, true);
                    metaInf.loadSettings(metaDir);
                    metaInf.loadProperties(metaDir);
                    metaInf.loadCNDs(metaDir);
                    metaInf.loadPrivileges(metaDir);
                    metaInf.setHasDefinition(new File(metaDir, Constants.PACKAGE_DEFINITION_XML).canRead());
                } catch (ConfigurationException e) {
                    log.warn("Error while loading meta dir." , e);
                } catch (IOException e) {
                    log.warn("Error while loading meta dir." , e);
                }
            }
        }
        return metaInf;
    }

    public File getRoot() {
        return rootDir;
    }

    public File getJcrRoot() {
        return jcrRoot;
    }

    public File getMetaDir() {
        return metaDir;
    }

    public static ExportRoot findRoot(File cwd) {
        if (cwd == null) {
            return null;
        }
        // find root
        File jcrRoot = new File(cwd, Constants.ROOT_DIR);
        if (!jcrRoot.exists() || !jcrRoot.isDirectory()) {
            jcrRoot = cwd;
            while (jcrRoot != null) {
                if (!jcrRoot.exists()) {
                    jcrRoot = null;
                } else {
                    if (jcrRoot.getName().equals(Constants.ROOT_DIR)) {
                        break;
                    }
                    jcrRoot = jcrRoot.getParentFile();
                }
            }
        }

        if (jcrRoot == null) {
            log.info("could not find " + Constants.ROOT_DIR + " along the ancestors of {}", cwd.getPath());
            return null;
        }
        return new ExportRoot(jcrRoot.getParentFile());
    }

}