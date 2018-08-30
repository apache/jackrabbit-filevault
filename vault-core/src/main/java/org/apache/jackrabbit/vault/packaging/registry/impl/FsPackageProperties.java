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

import java.util.Properties;

import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.impl.PackagePropertiesImpl;

public class FsPackageProperties extends PackagePropertiesImpl {
    
    private final FSInstallState installState;

    public FsPackageProperties(FSInstallState installState) {
        this.installState = installState;
    }

    @Override
    public PackageId getId() {
        return installState.getPackageId();
    }

    @Override
    public Dependency[] getDependencies() {
        if(installState.getDependencies() == null || installState.getDependencies().isEmpty()) {
            return Dependency.EMPTY;
        } else {
            return installState.getDependencies().toArray(new Dependency[installState.getDependencies().size()]);
        } 
    }
    
    @Override
    protected Properties getPropertiesMap() {
        return installState.getProperties();
    }
}
