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

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanInfo;
import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.JcrPackageManager;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code PackageManagerMBeanImpl} provide a MBean that lists all available packages as tabular data.
 */
@Component(
        service = DynamicMBean.class,
        immediate = true,
        property = {
                "service.vendor=The Apache Software Foundation",
                "jmx.objectname=org.apache.jackrabbit.vault.packaging:type=manager"
        }
)
public class PackageManagerMBeanImpl extends StandardMBean implements PackageManagerMBean {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(PackageManagerMBeanImpl.class);

    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    private SlingRepository repository;

    private static final String[] packageItemNames = {
            "id",
            "size",
            "installed",
            "installedDate",
            "installedBy"
    };
    private static final String[] packageItemDescriptions = {
            "Package Id",
            "Package Size",
            "Is installed",
            "Install Date",
            "Install User"
    };

    private static final OpenType[] packageItemTypes = {
            SimpleType.STRING,
            SimpleType.LONG,
            SimpleType.BOOLEAN,
            SimpleType.DATE,
            SimpleType.STRING
    };

    private static final String[] packageIndexNames = { "id" };

    private final CompositeType packageType;

    private final TabularType packageTabularType;

    public PackageManagerMBeanImpl() throws NotCompliantMBeanException, OpenDataException {
        super(PackageManagerMBean.class);
        packageType = new CompositeType("package", "Package Info", packageItemNames, packageItemDescriptions, packageItemTypes);
        packageTabularType = new TabularType("packages", "List of Packages", packageType, packageIndexNames);
    }

    @Override
    protected String getDescription(MBeanInfo info) {
        return "Package Manager Information";
    }

    @Override
    protected String getDescription(MBeanAttributeInfo info) {
        if ("Packages".equals(info.getName())) {
            return "Available Packages";
        }
        return super.getDescription(info);
    }

    @Override
    protected MBeanConstructorInfo[] getConstructors(MBeanConstructorInfo[] ctors, Object impl) {
        return null;
    }

    public TabularData getPackages() {
        TabularDataSupport packageData = new TabularDataSupport(packageTabularType);
        if (repository != null) {
            Session session = null;
            try {
                session = repository.loginAdministrative(null);
                // todo: find a way to use the sling packaging service instead
                JcrPackageManager pkgMgr = new JcrPackageManagerImpl(session, new String[0]);
                for (JcrPackage pkg: pkgMgr.listPackages()) {
                    try {
                        Object[] values = {
                                pkg.getDefinition().getId().toString(),
                                pkg.getSize(),
                                pkg.isInstalled(),
                                pkg.getDefinition().getLastUnpacked() == null ? null : pkg.getDefinition().getLastUnpacked().getTime(),
                                pkg.getDefinition().getLastUnpackedBy()
                        };
                        packageData.put(new CompositeDataSupport(packageType, packageItemNames, values));
                    } catch (Exception e) {
                        log.warn("Can't add composite data", e);
                    }
                    pkg.close();
                }
            } catch (RepositoryException e) {
                log.error("Repository error while retrieving package list", e);
            } finally {
                if (session != null) {
                    session.logout();
                }
            }
        } else {
            log.warn("Unable to provide package list. Repository not bound.");
        }
        return packageData;
	}
}