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
package org.apache.jackrabbit.vault.packaging.registry;

import javax.annotation.Nonnull;

import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.DependencyHandling;
import org.apache.jackrabbit.vault.packaging.PackageId;

/**
 * Reports dependency usages.
 */
public interface DependencyReport {

    /**
     * The id of the package this report is created for.
     * @return the package Id
     */
    @Nonnull
    PackageId getId();

    /**
     * Returns the dependencies that are not resolved. If the {@link DependencyHandling} is set to strict, the package
     * will not installed if any unresolved dependencies are listed.
     * @return the array of unresolved dependencies.
     */
    @Nonnull
    Dependency[] getUnresolvedDependencies();

    /**
     * Returns a list of the installed packages that this package depends on.
     * @return the array of resolved dependencies
     */
    @Nonnull
    PackageId[] getResolvedDependencies();

}