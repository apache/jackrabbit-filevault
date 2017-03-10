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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.jcr.RepositoryException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dependency Utilities
 */
public class DependencyUtil {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(DependencyUtil.class);

    /**
     * Sorts the packages by the dependency order
     * @param packages packages to sort
     * @throws CyclicDependencyException if a cyclic dependency is detected
     */
    public static <T extends VaultPackage> void sort(Collection<T> packages) throws CyclicDependencyException {
        Map<PackageId, Dependency[]> list = new LinkedHashMap<PackageId, Dependency[]>();
        Map<PackageId, VaultPackage> byId = new LinkedHashMap<PackageId, VaultPackage>();
        for (VaultPackage pack: packages) {
            list.put(pack.getId(), pack.getDependencies());
            byId.put(pack.getId(), pack);
        }
        packages.clear();
        for (PackageId id: resolve(list)) {
            packages.add((T) byId.remove(id));
        }
    }

    /**
     * Sorts the packages by the dependency order
     * @param packages packages to sort
     * @throws CyclicDependencyException if a cyclic dependency is detected
     * @throws javax.jcr.RepositoryException if an repository error occurs
     */
    public static <T extends JcrPackage> void sortPackages(Collection<T> packages)
            throws CyclicDependencyException, RepositoryException {
        Map<PackageId, Dependency[]> list = new LinkedHashMap<PackageId, Dependency[]>();
        Map<PackageId, JcrPackage> byId = new LinkedHashMap<PackageId, JcrPackage>();
        for (JcrPackage pack: packages) {
            PackageId id = pack.getDefinition().getId();
            list.put(id, pack.getDefinition().getDependencies());
            byId.put(id, pack);
        }
        packages.clear();
        for (PackageId id: resolve(list)) {
            packages.add((T) byId.remove(id));
        }

    }

    /**
     * Resolves a list of resolutions respecting their internal dependency references.
     * @param list list of resolutions
     * @return a new list of resolutions
     * @throws CyclicDependencyException if a cyclic dependency is detected
     */
    public static List<PackageId> resolve(Map<PackageId, Dependency[]> list) throws CyclicDependencyException {
        // create fake deplist
        Dependency[] fake = new Dependency[list.size()];
        int i=0;
        for (Map.Entry<PackageId, Dependency[]> entry: list.entrySet()) {
            fake[i++] = new Dependency(entry.getKey());
        }
        Map<PackageId, Boolean> result = new LinkedHashMap<PackageId, Boolean>(list.size());
        resolve(fake, list, result);
        return new ArrayList<PackageId>(result.keySet());
    }

    private static void resolve(Dependency[] deps, Map<PackageId, Dependency[]> list, Map<PackageId, Boolean> result)
            throws CyclicDependencyException {
        // find the dep in the list
        for (Dependency dep: deps) {
            for (Map.Entry<PackageId, Dependency[]> entry: list.entrySet()) {
                PackageId id = entry.getKey();
                if (dep.matches(id)) {
                    Boolean res = result.get(id);
                    if (res != null && !res) {
                        log.error("Package dependencies cause cycle.");
                        throw new CyclicDependencyException();
                    } else if (res == null) {
                        result.put(id, res = false);
                    }
                    resolve(entry.getValue(), list, result);
                    // shove at the end of the list if not resolved
                    if (!res) {
                        result.remove(id);
                        result.put(id, true);
                    }
                }
            }
        }
    }

    /**
     * Checks if any of the dependencies matches the given id
     * @param deps the list of dependencies
     * @param id the id
     * @return {@code true} if matches
     */
    public static boolean matches(@Nonnull Dependency[] deps, @Nonnull PackageId id) {
        for (Dependency dep: deps) {
            if (dep.matches(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds an exact dependency to the give package if it is not already contained in the given list.
     * @param deps the original dependencies
     * @param id the id to add
     * @return the new array of dependencies, or {@code deps} if nothing changed.
     */
    public static Dependency[] addExact(@Nonnull Dependency[] deps, @Nonnull PackageId id) {
        if (matches(deps, id)) {
            return deps;
        }
        Dependency[] newDeps = new Dependency[deps.length + 1];
        System.arraycopy(deps, 0, newDeps, 0, deps.length);
        newDeps[deps.length] = new Dependency(id);
        return newDeps;
    }

    /**
     * Adds an dependency to the give package if it is not already contained in the given list. version ranges are
     * current ignored.
     *
     * @param deps the original dependencies
     * @param dep the dependency to add
     * @return the new array of dependencies, or {@code deps} if nothing changed.
     */
    public static Dependency[] add(@Nonnull Dependency[] deps, @Nonnull Dependency dep) {
        for (Dependency d: deps) {
            if (d.getName().equals(dep.getName()) && d.getGroup().equals(dep.getGroup())) {
                return deps;
            }
        }
        Dependency[] newDeps = new Dependency[deps.length + 1];
        System.arraycopy(deps, 0, newDeps, 0, deps.length);
        newDeps[deps.length] = dep;
        return newDeps;
    }

}