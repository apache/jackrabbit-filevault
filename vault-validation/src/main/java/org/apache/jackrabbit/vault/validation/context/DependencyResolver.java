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
package org.apache.jackrabbit.vault.validation.context;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Map;

import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageInfo;
import org.jetbrains.annotations.NotNull;

/** Resolve the main meta information of a package dependency. */
public interface DependencyResolver {

    /**
     * Resolves multiple package dependencies taking into account a map from package id to URI (given via <a href="http://jackrabbit.apache.org/filevault/properties.html">package properties {@code dependencies-locations}</a>).
     * @param dependencies the dependencies to resolve
     * @param dependencyLocations a map of package ids to URIs
     * @return the list of {@link PackageInfo} for all resolved dependencies (as this is only a best effort implementation the list being returned might be smaller than the array size of {@code dependencies}).
     * @throws IOException in case some error happened during resolving
     */
    public @NotNull Collection<PackageInfo> resolvePackageInfo(@NotNull Dependency[] dependencies, @NotNull Map<PackageId, URI> dependencyLocations) throws IOException;
}
