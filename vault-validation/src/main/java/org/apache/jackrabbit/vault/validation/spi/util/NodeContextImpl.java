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
package org.apache.jackrabbit.vault.validation.spi.util;

import java.nio.file.Path;

import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.jetbrains.annotations.NotNull;

public final class NodeContextImpl implements NodeContext {
    private final @NotNull String nodePath;
    private final @NotNull Path filePath;
    private final @NotNull Path basePath;

    public NodeContextImpl(@NotNull String nodePath, @NotNull Path filePath, @NotNull Path basePath) {
        super();
        this.nodePath = nodePath;
        this.filePath = filePath;
        this.basePath = basePath;
    }

    @Override
    public @NotNull String getNodePath() {
        return nodePath;
    }

    @Override
    public @NotNull Path getFilePath() {
        return filePath;
    }

    @Override
    public @NotNull Path getBasePath() {
        return basePath;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((basePath == null) ? 0 : basePath.hashCode());
        result = prime * result + ((filePath == null) ? 0 : filePath.hashCode());
        result = prime * result + ((nodePath == null) ? 0 : nodePath.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        NodeContextImpl other = (NodeContextImpl) obj;
        if (basePath == null) {
            if (other.basePath != null)
                return false;
        } else if (!basePath.equals(other.basePath))
            return false;
        if (filePath == null) {
            if (other.filePath != null)
                return false;
        } else if (!filePath.equals(other.filePath))
            return false;
        if (nodePath == null) {
            if (other.nodePath != null)
                return false;
        } else if (!nodePath.equals(other.nodePath))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "NodeContextImpl [" + (nodePath != null ? "nodePath=" + nodePath + ", " : "")
                + (filePath != null ? "filePath=" + filePath + ", " : "") + (basePath != null ? "basePath=" + basePath : "") + "]";
    }
}