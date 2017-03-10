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

package org.apache.jackrabbit.vault.packaging;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.config.MetaInf;

/**
 * Holds options used for exporting.
 */
public class ExportOptions {

    private ProgressTrackerListener listener;

    private ExportPostProcessor postProcessor;

    private MetaInf metaInf;

    private String rootPath;

    private String mountPath;

    /**
     * Returns the progress tracker listener.
     * @return the progress tracker listener.
     */
    public ProgressTrackerListener getListener() {
        return listener;
    }

    /**
     * Sets the progress tracker listener for an export. The listener receives progress messages from the progress
     * tracker and can use them to provide feedback.
     * @param listener the listener
     */
    public void setListener(ProgressTrackerListener listener) {
        this.listener = listener;
    }

    /**
     * Returns the post processor
     * @return the post processor
     */
    public ExportPostProcessor getPostProcessor() {
        return postProcessor;
    }

    /**
     * Sets the export post processor for an export. The post processor is called after the actual export is performed
     * but before the archive is closed.
     * @param postProcessor the post processor
     */
    public void setPostProcessor(ExportPostProcessor postProcessor) {
        this.postProcessor = postProcessor;
    }

    /**
     * Returns the meta-inf
     * @return the meta-inf
     */
    public MetaInf getMetaInf() {
        return metaInf;
    }

    /**
     * Sets the meta-inf to be included in an exported archive.
     * @param metaInf the meta inf
     */
    public void setMetaInf(MetaInf metaInf) {
        this.metaInf = metaInf;
    }

    /**
     * Returns the root path.
     * @return the root path.
     */
    public String getRootPath() {
        return rootPath;
    }

    /**
     * Defines the root path where the mounted repository should be mapped into the vault fs. this can be used to
     * generate packages that have a virtual root. If a root path different than '/' is set, the workspace filter
     * will be adjusted accordingly, if possible.
     *
     * @param rootPath the root path
     */
    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    /**
     * Returns the mount path
     * @return the mount path
     */
    public String getMountPath() {
        return mountPath;
    }

    /**
     * Defines the root path where the repository should be mounted for the export. this can be used to generate
     * packages that are not "rooted" at '/'. If a mount path different than '/' is set, the workspace filter
     * will be adjusted accordingly, if possible
     *
     * @param mountPath the mount path
     */
    public void setMountPath(String mountPath) {
        this.mountPath = mountPath;
    }
}