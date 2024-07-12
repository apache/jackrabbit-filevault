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
import java.util.Objects;
import java.util.function.Function;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.jetbrains.annotations.NotNull;

public final class NodeContextImpl implements NodeContext {
    private final @NotNull String nodePath;
    private final @NotNull Path filePath;
    private final @NotNull Path basePath;
    private final int line;
    private final int column;
    private final Function<Name, String> jcrNameResolver;

    public NodeContextImpl(@NotNull String nodePath, @NotNull Path filePath, @NotNull Path basePath) {
        this(nodePath, filePath, basePath, 0, 0, Object::toString);
    }

    /**
     * 
     * @param nodePath
     * @param filePath
     * @param basePath
     * @param line
     * @param column
     * @deprecated Use {@link #NodeContextImpl(String, Path, Path, int, int, Function)} instead.
     */
    @Deprecated
    public NodeContextImpl(@NotNull String nodePath, @NotNull Path filePath, @NotNull Path basePath, int line, int column) {
        this(nodePath, filePath, basePath, line, column, Object::toString );
    }

    public NodeContextImpl(@NotNull String nodePath, @NotNull Path filePath, @NotNull Path basePath, int line, int column, @NotNull Function<Name, String> jcrNameResolver) {
        super();
        this.nodePath = nodePath;
        this.filePath = filePath;
        this.basePath = basePath;
        this.line = line;
        this.column = column;
        this.jcrNameResolver = jcrNameResolver;
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
    public int getLine() {
        return line;
    }

    @Override
    public int getColumn() {
        return column;
    }

    @Override
    public @NotNull String getJcrName(@NotNull Name name) {
        return jcrNameResolver.apply(name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(basePath, column, filePath, line, nodePath);
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
        return Objects.equals(basePath, other.basePath) && column == other.column && Objects.equals(filePath, other.filePath)
                && line == other.line && Objects.equals(nodePath, other.nodePath);
    }

    @Override
    public String toString() {
        return "NodeContextImpl [nodePath=" + nodePath + ", filePath=" + filePath + ", basePath=" + basePath + ", line=" + line
                + ", column=" + column + "]";
    }

}