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
package org.apache.jackrabbit.vault.packaging.events;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.jackrabbit.vault.packaging.PackageId;

import aQute.bnd.annotation.ProviderType;

/**
 * Event that is sent via the packaging listeners.
 */
@ProviderType
public interface PackageEvent {

    /**
     * Event type
     */
    enum Type {

        /**
         * Package was created
         */
        CREATE,

        /**
         * Package was uploaded
         */
        UPLOAD,

        /**
         * Package was installed (snapshot + extract)
         */
        INSTALL,

        /**
         * Package was extracted
         */
        EXTRACT,

        /**
         * Package was uninstalled
         */
        UNINSTALL,

        /**
         * Package was removed
         */
        REMOVE,

        /**
         * Package was assembled
         */
        ASSEMBLE,

        /**
         * Package was rewrapped
         */
        REWRAPP,

        /**
         * Package was renamed. {@link PackageEvent#getRelatedIds()} will contain the original package id/
         */
        RENAME,

        /**
         * Packages extracted the subpackages
         */
        EXTRACT_SUB_PACKAGES,

        /**
         * Package snapshot was taken.
         */
        SNAPSHOT
    }

    /**
     * Returns the type of the event
     * @return the type.
     */
    @Nonnull Type getType();

    /**
     * Returns the id of the package
     * @return the id.
     */
    @Nonnull PackageId getId();

    /**
     * Returns the related ids for certain events.
     * @return the related ids.
     */
    @CheckForNull PackageId[] getRelatedIds();

}