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
package org.apache.jackrabbit.vault.fs.nodetype.impl;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimpleRepositoryNodeTypesImpl extends AbstractRepositoryNodeTypes {

    private final String defaultPrimaryType;
    private final Collection<String> validPaths;

    public SimpleRepositoryNodeTypesImpl(String defaultPrimaryType, String... validPaths) {
        super();
        this.defaultPrimaryType = defaultPrimaryType;
        this.validPaths = Arrays.asList(validPaths);
    }

    @Override
    public boolean containsNodeTypes(String path) {
        return validPaths.contains(path);
    }

    @Override
    public @Nullable String getPrimaryNodeType(String path) {
        if (validPaths.contains(path)) {
            return defaultPrimaryType;
        } else {
            return null;
        }
    }

    @Override
    public @NotNull Set<String> getMixinNodeTypes(String path) {
        return Collections.emptySet();
    }
}
