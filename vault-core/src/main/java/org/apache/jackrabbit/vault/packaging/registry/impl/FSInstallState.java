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
package org.apache.jackrabbit.vault.packaging.registry.impl;

import java.util.Collections;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;

/**
 * Internal (immutable) State object to cache and pass the relevant metadata around.
 */
public class FSInstallState {

    private PackageId packageId;
    private FSPackageStatus status;
    private String filePath;
    private boolean external;
    private Set<Dependency> dependencies = Collections.emptySet();
    private Set<PackageId> subPackages = Collections.emptySet();
    private Long installTime;

    public FSInstallState(@Nonnull PackageId packageId, @Nonnull FSPackageStatus status, @Nullable String filePath,
            boolean external, @Nullable Set<Dependency> dependencies, @Nullable Set<PackageId> subPackages, @Nullable Long installTime) {
        this.packageId = packageId;
        this.status = status;
        this.filePath = filePath;
        this.external = external;
        if (dependencies != null) {
            this.dependencies = Collections.unmodifiableSet(dependencies);
        }
        this.dependencies = dependencies;
        this.installTime = installTime;
        if (subPackages != null) {
            this.subPackages = Collections.unmodifiableSet(subPackages);
        }
    }

    public Long getInstallationTime() {
        return installTime;
    }

    public Set<PackageId> getSubPackages() {
        return subPackages;
    }

    public PackageId getPackageId() {
        return packageId;
    }

    public String getFilePath() {
        return filePath;
    }

    public boolean isExternal() {
        return external;
    }

    public FSPackageStatus getStatus() {
        return status;
    }

    public Set<Dependency> getDependencies() {
        return dependencies;
    }
}