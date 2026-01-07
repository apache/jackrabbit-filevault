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
package org.apache.jackrabbit.vault.packaging.registry.impl;

import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.registry.DependencyReport;
import org.jetbrains.annotations.NotNull;

/**
 * {@code UsageReportImpl}...
 */
public class DependencyReportImpl implements DependencyReport {

    private final PackageId id;

    private final Dependency[] unresolved;

    private final PackageId[] resolved;

    public DependencyReportImpl(PackageId id, Dependency[] unresolved, PackageId[] resolved) {
        this.id = id;
        this.unresolved = unresolved;
        this.resolved = resolved;
    }

    @NotNull
    @Override
    public PackageId getId() {
        return id;
    }

    @NotNull
    @Override
    public Dependency[] getUnresolvedDependencies() {
        return unresolved;
    }

    @NotNull
    @Override
    public PackageId[] getResolvedDependencies() {
        return resolved;
    }
}
