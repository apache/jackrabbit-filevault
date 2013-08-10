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

package org.apache.jackrabbit.vault.packaging;

import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.io.ImportOptions;

/**
 * The install context is passed to {@link InstallHook}s during the different
 * phases of a package installation.
 */
public interface InstallContext {

    /**
     * The current phase of a package installation
     */
    enum Phase {

        /**
         * Specifies that the package is not yet installed and the hooks can
         * do some pre-installation work
         */
        PREPARE,

        /**
         * Specifies that the prepare phase failed and the hocks can do some
         * cleanup work.
         */
        PREPARE_FAILED,

        /**
         * Specifies that the package was successfully installed and the hooks
         * can do some post-installation work and cleanup.
         */
        INSTALLED,

        /**
         * Specifies that the package installation failed and the hooks can
         * do some cleanup work.
         */
        INSTALL_FAILED,

        /**
         * Specifies that the hook is going to be discarded. this is guaranteed
         * to be called at the end of an installation process.
         */
        END
    }

    /**
     * Returns the current installation phase
     * @return the phase
     */
    Phase getPhase();

    /**
     * Returns the session that is used to install the package
     * @return the session
     */
    Session getSession();

    /**
     * Returns the package that is currently installed
     * @return the vault package
     */
    VaultPackage getPackage();

    /**
     * Returns the import options that are used to install the package
     * @return the import options
     */
    ImportOptions getOptions();

}