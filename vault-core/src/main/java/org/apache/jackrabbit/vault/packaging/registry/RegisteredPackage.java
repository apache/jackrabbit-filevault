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

import java.io.IOException;
import java.util.Calendar;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.osgi.annotation.versioning.ProviderType;

/**
 * {@code RegisteredPackage}...
 */
@ProviderType
public interface RegisteredPackage extends Comparable<RegisteredPackage>, AutoCloseable {

    /**
     * Returns the id of this package
     * @return the id of this package.
     */
    @Nonnull
    PackageId getId();

    /**
     * Returns the vault package stored in the data of this package
     * @return the package
     * @throws IOException if an I/O error occurs
     */
    @Nonnull
    VaultPackage getPackage() throws IOException;

    /**
     * Returns the size of the underlying package.
     * @return the size in bytes
     */
    long getSize();

    /**
     * Checks if this package is installed.
     * @return {@code true} if this package is installed.
     */
    boolean isInstalled();

    /**
     * Returns the date when the package was installed
     * @return the installed date or {@code null} if not installed.
     */
    @Nullable
    Calendar getInstallationTime();

    /**
     * Closes this package and releases underlying data.
     */
    void close();

}