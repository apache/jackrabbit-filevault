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
package org.apache.jackrabbit.vault.fs.io;

import java.io.IOException;

import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.packaging.impl.OsgiAwarePropertiesUtil;
import org.apache.jackrabbit.vault.util.Constants;
import org.h2.util.CloseWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for archives
 */
abstract class AbstractArchive implements Archive {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(AbstractArchive.class);

    protected static final String PROPERTY_ENABLE_STACK_TRACES = "vault.enableStackTraces";

    /**
     * Determines whether stack traces should be created for each register call of {@link CloseWatcher}.
     * This is false by default.
     * Enable via system or OSGi framework property {@code vault.enableStackTraces}.
     */
    protected static final boolean SHOULD_CREATE_STACK_TRACE =
            OsgiAwarePropertiesUtil.getBooleanProperty(PROPERTY_ENABLE_STACK_TRACES);

    @Override
    public Entry getEntry(String path) throws IOException {
        String[] segs = Text.explode(path, '/');
        Entry root = getRoot();
        for (String name : segs) {
            root = root.getChild(name);
            if (root == null) {
                break;
            }
        }
        return root;
    }

    @Override
    public Entry getJcrRoot() throws IOException {
        return getRoot().getChild(Constants.ROOT_DIR);
    }

    @Override
    public Archive getSubArchive(String rootPath, boolean asJcrRoot) throws IOException {
        Entry root = getEntry(rootPath);
        return root == null ? null : new SubArchive(this, root, asJcrRoot);
    }

    static boolean dumpUnclosedArchives() {
        boolean foundUnclosedArchive = false;
        while (true) {
            CloseWatcher w = CloseWatcher.pollUnclosed();
            if (w == null) {
                break;
            }
            foundUnclosedArchive = true;

            if (SHOULD_CREATE_STACK_TRACE) {
                log.error("Detected unclosed archive, it has been opened here:\n{}", w.getOpenStackTrace());
            } else {
                log.error(
                        "Detected unclosed archive. To figure out where it has been opened set the Java System property '{}' to 'true'",
                        PROPERTY_ENABLE_STACK_TRACES);
            }
            try {
                AutoCloseable closeable = w.getCloseable();
                if (closeable != null) {
                    closeable.close();
                }
            } catch (Exception e) {
                log.error("Error forcing closing archive", e);
            }
        }
        return foundUnclosedArchive;
    }
}
