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

import java.util.Calendar;
import java.util.Properties;

import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.SubPackageHandling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * package properties abstraction
 */
public abstract class PackagePropertiesImpl implements PackageProperties {

    private static final Logger log = LoggerFactory.getLogger(PackagePropertiesImpl.class);

    public static final String UNKNOWN_PATH = "/etc/packages/unknown";

    private PackageId id;

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
                // check for legacy packages that only contains a 'path' property
                String path = getProperty("path");
                if (path == null || path.length() == 0) {
                    log.warn("Package does not specify a path. setting to 'unknown'");
                    path = UNKNOWN_PATH;
                }
                id = new PackageId(path, version);
            }
        }
        return id;
    }

    /**
     * {@inheritDoc}
     */
    public Calendar getLastModified() {
        return getDateProperty(NAME_LAST_MODIFIED);
    }

    /**
     * {@inheritDoc}
     */
    public String getLastModifiedBy() {
        return getProperty(NAME_LAST_MODIFIED_BY);
    }

    /**
     * {@inheritDoc}
     */
    public Calendar getCreated() {
        return getDateProperty(NAME_CREATED);
    }

    /**
     * {@inheritDoc}
     */
    public String getCreatedBy() {
        return getProperty(NAME_CREATED_BY);
    }

    /**
     * {@inheritDoc}
     */
    public Calendar getLastWrapped() {
        return getDateProperty(NAME_LAST_WRAPPED);
    }

    /**
     * {@inheritDoc}
     */
    public String getLastWrappedBy() {
        return getProperty(NAME_LAST_WRAPPED_BY);
    }

    /**
     * {@inheritDoc}
     */
    public String getDescription() {
        return getProperty(NAME_DESCRIPTION);
    }

    /**
     * {@inheritDoc}
     */
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
    public SubPackageHandling getSubPackageHandling() {
        return SubPackageHandling.fromString(getProperty(NAME_SUB_PACKAGE_HANDLING));
    }

    /**
     * {@inheritDoc}
     */
    public boolean requiresRoot() {
        return "true".equals(getProperty(NAME_REQUIRES_ROOT));
    }

    /**
     * {@inheritDoc}
     */
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
    public String getProperty(String name) {
        try {
            Properties props = getPropertiesMap();
            return props == null ? null : props.getProperty(name);
        } catch (Exception e) {
            return null;
        }
    }

    protected abstract Properties getPropertiesMap();


}