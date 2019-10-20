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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.annotation.Nullable;

import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.packaging.SubPackageHandling;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * package properties abstraction
 */
public abstract class PackagePropertiesImpl implements PackageProperties {

    private static final Logger log = LoggerFactory.getLogger(PackagePropertiesImpl.class);

    private PackageId id;

    @Override
    public PackageId getId() {
        if (id == null) {
            String version = getProperty(NAME_VERSION);
            if (version == null) {
                log.warn("Package does not specify a version. setting to ''");
                 version = "";
            }
            String group = getProperty(NAME_GROUP);
            String name = getProperty(NAME_NAME);
            if (group != null && name != null) {
                id = new PackageId(group, name, version);
            } else {
                log.warn("Package properties not valid. need group and name property.");
            }
        }
        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Calendar getLastModified() {
        return getDateProperty(NAME_LAST_MODIFIED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLastModifiedBy() {
        return getProperty(NAME_LAST_MODIFIED_BY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Calendar getCreated() {
        return getDateProperty(NAME_CREATED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCreatedBy() {
        return getProperty(NAME_CREATED_BY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Calendar getLastWrapped() {
        return getDateProperty(NAME_LAST_WRAPPED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLastWrappedBy() {
        return getProperty(NAME_LAST_WRAPPED_BY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return getProperty(NAME_DESCRIPTION);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AccessControlHandling getACHandling() {
        String ac = getProperty(NAME_AC_HANDLING);
        if (ac == null) {
            return AccessControlHandling.IGNORE;
        } else {
            try {
                return AccessControlHandling.valueOf(ac.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("invalid access control handling configured: {}", ac);
                return AccessControlHandling.IGNORE;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SubPackageHandling getSubPackageHandling() {
        return SubPackageHandling.fromString(getProperty(NAME_SUB_PACKAGE_HANDLING));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean requiresRoot() {
        return "true".equals(getProperty(NAME_REQUIRES_ROOT));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Dependency[] getDependencies() {
        String deps = getProperty(NAME_DEPENDENCIES);
        if (deps == null) {
            return Dependency.EMPTY;
        } else {
            return Dependency.parse(deps);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<PackageId, URI> getDependenciesLocations() {
        String deps = getProperty(NAME_DEPENDENCIES_LOCATIONS);
        if (deps == null || deps.length() == 0) {
            return Collections.emptyMap();
        } else {
            // parse item in the format <pid>=<uri>, items are comma separated
            Map<PackageId, URI> dependenciesLocations = new HashMap<>();
            for (String item : deps.split(",")) {
                String[] parts = item.split("=", 2);
                if (parts.length < 2) {
                    log.error("Invalid dependencies locations string, item " +item + " does not contain a '='");
                } else {
                    PackageId packageId = PackageId.fromString(parts[0]);
                    if (packageId == null) {
                        log.error("Invalid package id given in item " + item);
                        continue;
                    }
                    try {
                        URI uri = new URI(parts[1]);
                        dependenciesLocations.put(packageId, uri);
                    } catch (URISyntaxException e) {
                        log.error("Invalid uri given in item " + item);
                    }
                    
                }
            }
            return dependenciesLocations;
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Calendar getDateProperty(String name) {
        try {
            String p = getProperty(name);
            return p == null
                    ? null
                    : ISO8601.parse(p);
        } catch (Exception e) {
            log.error("Error while converting date property", e);
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProperty(String name) {
        try {
            Properties props = getPropertiesMap();
            return props == null ? null : props.getProperty(name);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public PackageType getPackageType() {
        final String pt = getProperty(NAME_PACKAGE_TYPE);
        if (pt != null) {
            try {
                return PackageType.valueOf(pt.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid package type configured: {}", pt);
            }
        }
        return null;
    }

    @Override
    public Map<String, String> getExternalHooks() {
        // currently only the format: "installhook.{name}.class" is supported
        Properties props = getPropertiesMap();
        Map<String, String> hookClasses = new HashMap<>();
        if (props != null) {
            Enumeration<?> names = props.propertyNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement().toString();
                if (name.startsWith(VaultPackage.PREFIX_INSTALL_HOOK)) {
                    String[] segs = Text.explode(name.substring(VaultPackage.PREFIX_INSTALL_HOOK.length()), '.');
                    if (segs.length == 0 || segs.length > 2 || !"class".equals(segs[1])) {
                        log.warn("Invalid installhook property: {}", name);
                    }
                    hookClasses.put(segs[0], props.getProperty(name));
                }
            }
        }
        return hookClasses;
    }

    protected abstract Properties getPropertiesMap();


}