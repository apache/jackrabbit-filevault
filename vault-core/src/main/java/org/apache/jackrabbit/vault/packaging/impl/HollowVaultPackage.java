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
package org.apache.jackrabbit.vault.packaging.impl;

import java.io.File;
import java.util.Properties;

import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;

/**
 * Implements a vault package that is a hollow representation of a file vault
 * export i.e. when original zipped representation has been truncated.
 */
public class HollowVaultPackage extends PackagePropertiesImpl implements VaultPackage {

    private Properties properties;

    public HollowVaultPackage(Properties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public boolean isClosed() {
        return true;
    }

    @Override
    public MetaInf getMetaInf() {
        return null;
    }

    @Override
    public long getSize() {
        return -1;
    }

    @Override
    public void extract(Session session, ImportOptions opts) throws PackageException {
        throw new PackageException("extract operation not supported by hollow package");
    }

    @Override
    public File getFile() {
        return null;
    }

    @Override
    public void close() {
    }

    @Override
    public Archive getArchive() {
        return null;
    }

    @Override
    public PackageProperties getProperties() {
        return this;
    }

    @Override
    protected Properties getPropertiesMap() {
        return properties;
    }
}
