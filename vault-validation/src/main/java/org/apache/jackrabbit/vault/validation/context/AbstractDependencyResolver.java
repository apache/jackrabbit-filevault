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
package org.apache.jackrabbit.vault.validation.context;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageInfo;
import org.apache.jackrabbit.vault.packaging.VersionRange;
import org.apache.maven.artifact.Artifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract package dependency resolver supporting Maven repository dependency location URIs (starting with {@code maven:}).
 * All package dependencies are mapped to Maven coordinates by this class and then resolved via {@link #resolvePackageInfo(MavenCoordinates)}.
 * The resolver comes with a cache so that the same package dependency is not resolved more than once.
 * This class is not thread-safe.
 * @see PackageInfo
 */
public abstract class AbstractDependencyResolver implements DependencyResolver {

    /**
     * the default logger
     */
    private static final Logger log = LoggerFactory.getLogger(AbstractDependencyResolver.class);

    public static final String MAVEN_REPOSITORY_SCHEME = "maven";
    private final Collection<PackageInfo> packageInfoCache;

    protected AbstractDependencyResolver() {
        this.packageInfoCache = new LinkedList<>();
    }

    protected AbstractDependencyResolver(@NotNull Collection<PackageInfo> packageInfoCache) {
        this.packageInfoCache = new LinkedList<>(packageInfoCache);
    }

    @Override
    public @NotNull Collection<PackageInfo> resolvePackageInfo(
            @NotNull Dependency[] dependencies, @NotNull Map<PackageId, URI> dependencyLocations) throws IOException {
        List<PackageInfo> packageInfos = new LinkedList<>();
        // resolve dependencies
        for (Dependency dependency : dependencies) {
            PackageInfo packageInfo = null;
            // 1. try to get from cache
            for (PackageInfo packageInfoFromCache : packageInfoCache) {
                if (dependency.matches(packageInfoFromCache.getId())) {
                    log.debug("Dependency is already resolved from project dependencies: {}", dependency);
                    packageInfo = packageInfoFromCache;
                }
            }
            // 2. try to resolve via provided dependency location URIs
            if (packageInfo == null) {
                for (Map.Entry<PackageId, URI> dependencyLocation : dependencyLocations.entrySet()) {
                    if (dependency.matches(dependencyLocation.getKey())) {
                        MavenCoordinates coords = MavenCoordinates.parse(dependencyLocation.getValue());
                        if (coords != null) {
                            packageInfo = resolvePackageInfo(coords);
                        } else {
                            log.warn(
                                    "Could not resolve Maven coordinates for dependency location URI: {}",
                                    dependencyLocation.getValue());
                        }
                    }
                }
            }
            // 3. try to apply some heuristics
            if (packageInfo == null) {
                packageInfo = resolvePackageInfo(dependency);
            }
            if (packageInfo != null) {
                packageInfos.add(packageInfo);
                if (packageInfoCache.contains(packageInfo)) {
                    packageInfoCache.add(packageInfo);
                }
            }
        }
        return packageInfos;
    }

    /**
     * Uses some heuristics to map the package dependency to Maven coordinates.
     * It tries to resolve the package version ranges in the following order:
     * <ol>
     * <li>trying lower bound version</li>
     * <li>trying higher bound version</li>
     * <li>trying LATEST version</li>
     * </ol>
     * Each one is triggering {@link #resolvePackageInfo(MavenCoordinates)}.
     * It doesn't leverage any Maven API for version range resolution, therefore this method should be overwritten if there are better ways to resolve version ranges.
     * @param dependency the dependency from which to retrieve the package info
     * @return the resolved package info or {@code null}
     * @throws IOException
     */
    protected @Nullable PackageInfo resolvePackageInfo(@NotNull Dependency dependency) throws IOException {
        // resolving a version range is not supported with Maven API, but only with lower level Aether API (requires
        // Maven 3.5 or newer)
        // https://github.com/eclipse/aether-demo/blob/master/aether-demo-snippets/src/main/java/org/eclipse/aether/examples/FindAvailableVersions.java
        // therefore do an best effort resolve instead
        final String groupId = dependency.getGroup();
        final String artifactId = dependency.getName();
        PackageInfo info = null;
        if (dependency.getRange().isLowInclusive()) {
            info = resolvePackageInfo(new MavenCoordinates(
                    groupId, artifactId, dependency.getRange().getLow().toString()));
        }
        if (info == null && dependency.getRange().isHighInclusive()) {
            info = resolvePackageInfo(new MavenCoordinates(
                    groupId, artifactId, dependency.getRange().getHigh().toString()));
        }
        if (info == null && VersionRange.INFINITE.equals(dependency.getRange())) {
            info = resolvePackageInfo(new MavenCoordinates(groupId, artifactId, Artifact.LATEST_VERSION));
        }
        if (info == null) {
            return null;
        }
        return info;
    }

    /**
     * Resolves the main meta information of a package dependency given via Maven coordinates.
     * @param mavenCoordinates the coordinates of the package dependency
     * @return the resolved package info or {@code null}
     * @throws IOException
     */
    public abstract @Nullable PackageInfo resolvePackageInfo(@NotNull MavenCoordinates mavenCoordinates)
            throws IOException;

    /** Encapsulates Maven coordinates groupId, artifactId, version, packaging (default {@code zip}), classifier (optional) */
    public static final class MavenCoordinates {
        @NotNull
        private final String groupId;

        @NotNull
        private final String artifactId;

        @NotNull
        private final String version;

        @NotNull
        private final String packaging;

        @Nullable
        private final String classifier;

        private static final String DEFAULT_PACKAGING = "zip";

        public MavenCoordinates(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
            this(groupId, artifactId, version, DEFAULT_PACKAGING, null);
        }

        public MavenCoordinates(
                @NotNull String groupId,
                @NotNull String artifactId,
                @NotNull String version,
                @NotNull String packaging,
                String classifier) {
            super();
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.packaging = packaging;
            this.classifier = classifier;
        }

        /** Parses a Maven URI of the format {@code maven:<groupId>:<artifactId>:<version>[:<packaging>[:<classifier]]} and returns the extracted Maven coordinates.
         * @param uri
         * @return the Maven coordinates or {@code null} if the URI is not a Maven URI
         */
        public static @Nullable MavenCoordinates parse(URI uri) {
            if (!MAVEN_REPOSITORY_SCHEME.equals(uri.getScheme())) {
                return null;
            }
            if (!uri.isOpaque()) {
                throw new IllegalArgumentException("Only opaque Maven URIs are supported");
            }
            // support groupId, artifactId, packaging and classifier (format like
            // https://maven.apache.org/plugins/maven-dependency-plugin/get-mojo.html#artifact)
            // extract group id and artifact id
            String[] parts = uri.getSchemeSpecificPart().split(":");
            if (parts.length < 3) {
                throw new IllegalArgumentException(
                        "At least group id, artifact id and version need to be given separated by ':'");
            }
            String groupId = parts[0];
            String artifactId = parts[1];
            String version = parts[2];

            String packaging = DEFAULT_PACKAGING;
            if (parts.length > 3) {
                packaging = parts[3];
            }
            String classifier = null;
            if (parts.length > 4) {
                classifier = parts[4];
            }
            return new MavenCoordinates(groupId, artifactId, version, packaging, classifier);
        }

        public @NotNull String getGroupId() {
            return groupId;
        }

        public @NotNull String getArtifactId() {
            return artifactId;
        }

        public @Nullable String getVersion() {
            return version;
        }

        public @NotNull String getPackaging() {
            return packaging;
        }

        public @Nullable String getClassifier() {
            return classifier;
        }

        @Override
        public String toString() {
            return "MavenCoordinates [" + (groupId != null ? "groupId=" + groupId + ", " : "")
                    + (artifactId != null ? "artifactId=" + artifactId + ", " : "")
                    + (version != null ? "version=" + version + ", " : "")
                    + (packaging != null ? "packaging=" + packaging + ", " : "")
                    + (classifier != null ? "classifier=" + classifier : "")
                    + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(artifactId, classifier, groupId, packaging, version);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            MavenCoordinates other = (MavenCoordinates) obj;
            return Objects.equals(artifactId, other.artifactId)
                    && Objects.equals(classifier, other.classifier)
                    && Objects.equals(groupId, other.groupId)
                    && Objects.equals(packaging, other.packaging)
                    && Objects.equals(version, other.version);
        }
    }
}
