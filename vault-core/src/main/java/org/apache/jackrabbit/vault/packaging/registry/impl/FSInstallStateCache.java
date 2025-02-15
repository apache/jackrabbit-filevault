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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.AbstractMap;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.apache.jackrabbit.vault.packaging.PackageId;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persisted cache of all {@link FSInstallState} objects for all packages in a registry.
 * Populated on demand and written back immediately for every modifying operation.
 * All map operations might throw {@link java.io.UncheckedIOException}.
 * Is thread-safe.
 */
class FSInstallStateCache extends AbstractMap<PackageId, FSInstallState> {

    private static final Logger log = LoggerFactory.getLogger(FSInstallStateCache.class);

    /**
     * Extension for metadata files
     */
    private static final String META_EXTENSION = ".xml";

    private final Map<PackageId, FSInstallState> cache = new ConcurrentHashMap<>();
    private boolean isInitialized = false;

    /**
     * Contains a map of all filesystem paths to package IDs
     */
    private Map<Path, PackageId> pathIdMapping = new ConcurrentHashMap<>();

    private final Path homeDir;

    public FSInstallStateCache(Path homeDir) throws IOException {
        this.homeDir = homeDir;
        log.debug("checking for presence of {} - exists {} - isDirectory {}", homeDir, Files.exists(homeDir), Files.isDirectory(homeDir));
        if (!Files.exists(homeDir)) {
            Path created = Files.createDirectories(homeDir);
            log.debug("Created {}", created);
        } else {
            if (!Files.isDirectory(homeDir)) {
                String message = homeDir + " exists, but is not a directory - aborting";
                log.error(message);
                throw new IOException(message);
            }
        }
    }

    /**
     * Loads all state from files persisted in configured homeDir, adds to cache and returns all cached {@code PackageId}s.
     * @throws IOException 
     */
    private synchronized void load() throws IOException {
        Map<PackageId, FSInstallState> cacheEntries = new HashMap<>();
        Map<Path, PackageId> idMapping = new HashMap<>();

        // recursively find meta file
        log.debug("loading state from home directory {}", homeDir);
        try (Stream<Path> stream = Files.walk(homeDir, 10, FileVisitOption.FOLLOW_LINKS)) {
            stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(META_EXTENSION)).forEach(
                p -> {
                    FSInstallState state;
                    try {
                        state = FSInstallState.fromFile(p);
                        log.debug("loaded state from {}", p);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    if (state != null) {
                        PackageId id = state.getPackageId();
                        if (id != null) {
                            cacheEntries.put(id, state);
                            idMapping.put(state.getFilePath(), id);
                        }
                    }
                }
            );
        }

        cache.putAll(cacheEntries);
        pathIdMapping.putAll(idMapping);
        isInitialized = true;
    }

    @Override
    public Set<Entry<PackageId, FSInstallState>> entrySet() {
        if (!isInitialized) {
            try {
                load();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return cache.entrySet();
    }

    /**
     * Returns the meta data file of the package with the given Id.
     *
     * @param id The package Id.
     * @return the meta data file.
     */
    @NotNull
    private Path getPackageMetaDataFile(@NotNull PackageId id) {
        final String path = AbstractPackageRegistry.getRelativeInstallationPath(id);
        return homeDir.resolve(path + ".xml");
    }

    @NotNull
    public Path getPackageFile(@NotNull PackageId id) {
        String path = AbstractPackageRegistry.getRelativeInstallationPath(id);
        return homeDir.resolve(path + ".zip");
    }


    /**
     * Shortcut to just change the status of a package - implicitly sets the installtime when switching to EXTRACTED
     *
     * @param pid PackageId of the package to update
     * @param targetStatus Status to update
     * @throws IOException If an I/O error occurs.
     */
    public void updatePackageStatus(PackageId pid, FSPackageStatus targetStatus) throws IOException {
        FSInstallState state = get(pid);
        if (state == null) {
            throw new IllegalArgumentException("No package with pid " + pid + " registered");
        }
        Long installTime = state.getInstallationTime();
        if (FSPackageStatus.EXTRACTED == targetStatus) {
            installTime = Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC), Locale.ROOT).getTimeInMillis();
        }
        FSInstallState targetState = new FSInstallState(pid, targetStatus, state.getFilePath())
              .withDependencies(state.getDependencies())
              .withSubPackages(state.getSubPackages())
              .withInstallTime(installTime)
              .withSize(state.getSize())
              .withProperties(state.getProperties())
              .withExternal(state.isExternal());
        put(pid, targetState);
    }

    @Override
    public FSInstallState get(Object key) {
        FSInstallState state = super.get(key);
        if (state == null) {
            PackageId pid = (PackageId) key;
            // fallback (only for get(..), but does not affect size(), entrySet(), hasKey(), keys(), values()), detects changes on the filesystem done outside this class
            Path metaFile = getPackageMetaDataFile(pid);
            try {
                state = FSInstallState.fromFile(metaFile);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (state != null) {
                cache.put(pid, state);
                pathIdMapping.put(state.getFilePath(), pid);
            }
        }
        return state;
    }

    @Override
    public FSInstallState put(PackageId key, FSInstallState value) {
        FSInstallState state = cache.put(key, value);
        // persist changes
        try {
            value.save(getPackageMetaDataFile(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return state;
    }

    @Override
    public FSInstallState remove(Object key) {
        FSInstallState state = super.remove(key);
        if (state != null) {
            PackageId pid = (PackageId) key;
            Path metaData = getPackageMetaDataFile(pid);
            try {
                Files.delete(metaData);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return state;
    }

    public PackageId getIdForFile(Path file) {
        return pathIdMapping.get(file);
    }
}
