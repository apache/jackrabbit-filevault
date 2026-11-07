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
package org.apache.jackrabbit.vault.vlt;

/**
 * Validates remote and metadata names before the vlt CLI uses them as local file names.
 */
public class FileSystemNameValidator {

    private FileSystemNameValidator() {
        // static utility
    }

    /**
     * Checks whether {@code name} refers to the local vlt control state (the {@code .vlt}
     * meta directory / control zip), case-insensitively. A remote name using this name must
     * never be checked out/synced onto the local file system, as it would collide with the
     * working copy's own control file.
     *
     * @param name a file name, as delivered by a remote source (e.g. {@code VaultFile#getName()}).
     * @return {@code true} if the name is reserved for the local vlt control state.
     */
    public static boolean isVltControlName(String name) {
        if (name == null) {
            return false;
        }
        // Windows silently strips trailing dots/spaces from a path segment on file
        // creation, so ".vlt." or ".vlt " would land on the same file as ".vlt".
        String normalized = name;
        int end = normalized.length();
        while (end > 0 && (normalized.charAt(end - 1) == '.' || normalized.charAt(end - 1) == ' ')) {
            end--;
        }
        normalized = normalized.substring(0, end);
        return VltDirectory.META_DIR_NAME.equalsIgnoreCase(normalized);
    }
}
