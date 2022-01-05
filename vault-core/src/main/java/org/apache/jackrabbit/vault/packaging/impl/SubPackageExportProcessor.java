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

import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.Mounter;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.PathMapping;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.SimplePathMapping;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.fs.api.VaultFileSystem;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.AbstractExporter;
import org.apache.jackrabbit.vault.packaging.ExportPostProcessor;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.registry.impl.AbstractPackageRegistry;
import org.apache.jackrabbit.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.jackrabbit.vault.packaging.registry.impl.JcrPackageRegistry.DEFAULT_PACKAGE_ROOT_PATH;

/**
 * Helper class to handle sub-package exporting for non /etc/package package roots
 */
public class SubPackageExportProcessor implements ExportPostProcessor {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(SubPackageExportProcessor.class);

    private JcrPackageManagerImpl mgr;

    private final Session session;

    private final LinkedHashMap<PackageId, String> subPackages = new LinkedHashMap<>();

    public SubPackageExportProcessor(JcrPackageManagerImpl jcrPackageManager, Session session) {
        this.mgr = jcrPackageManager;
        this.session = session;
    }

    public void process(AbstractExporter exporter) {
        try {
            for (Map.Entry<PackageId, String> pkg : subPackages.entrySet()) {
                String nodePath = pkg.getValue();
                // skip the ones already below /etc/packages
                if (Text.isDescendantOrEqual(DEFAULT_PACKAGE_ROOT_PATH, nodePath)) {
                    continue;
                }
                mgr.getInternalRegistry();
                String etcPath = DEFAULT_PACKAGE_ROOT_PATH + "/" + AbstractPackageRegistry.getRelativeInstallationPath(pkg.getKey()) + ".zip";
                etcPath = Text.getRelativeParent(etcPath, 1);

                // define a workspace filter for the package at the real location
                DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
                filter.add(new PathFilterSet(nodePath));

                // mount the repository at the group node level
                RepositoryAddress addr;
                try {
                    addr = new RepositoryAddress(Text.escapePath("/" + session.getWorkspace().getName() + Text.getRelativeParent(nodePath, 1)));
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException(e);
                }
                VaultFileSystem jcrfs = Mounter.mount(null, filter, addr, etcPath, session);

                // perform the export of the first level here, so that we can exclude the .content.xml of the group node
                for (VaultFile vaultFile : jcrfs.getRoot().getChildren()) {
                    String path = "jcr_root/" + vaultFile.getName();
                    if (vaultFile.isDirectory()) {
                        exporter.createDirectory(vaultFile, path);
                        exporter.export(vaultFile, path);
                    } else {
                        // edge case - we don't export the .content.xml of the group node
                        if (!path.endsWith("/.content.xml")) {
                            exporter.writeFile(vaultFile, path);
                        }
                    }
                }
                jcrfs.unmount();
            }
        } catch (Exception e) {
            log.error("Error during post processing", e);
        }
    }

    public WorkspaceFilter prepare(final WorkspaceFilter originalFilter) throws RepositoryException {
        for (JcrPackage pkg: mgr.listPackages(originalFilter)) {
            if (pkg.isValid() && pkg.getSize() > 0) {
                subPackages.put(pkg.getDefinition().getId(), pkg.getNode().getPath());
            }
        }
        // now also get the packages from the primary root
        WorkspaceFilter filter = originalFilter.translate(new SimplePathMapping(DEFAULT_PACKAGE_ROOT_PATH, mgr.getInternalRegistry().getPackRootPaths()[0]));
        for (JcrPackage pkg: mgr.listPackages(filter)) {
            if (pkg.isValid() && pkg.getSize() > 0) {
                subPackages.put(pkg.getDefinition().getId(), pkg.getNode().getPath());
            }
        }
        if (subPackages.size() > 0) {
            // now remove the filters with the sub-package information and create distinct ones for the sub packages
            DefaultWorkspaceFilter newFilter = (DefaultWorkspaceFilter) filter.translate(PathMapping.IDENTITY);
            Iterator<PathFilterSet> iter = newFilter.getFilterSets().iterator();
            while (iter.hasNext()) {
                PathFilterSet set = iter.next();
                for (String root : mgr.getInternalRegistry().getPackRootPaths()) {
                    if (Text.isDescendantOrEqual(root, set.getRoot())) {
                        iter.remove();
                        break;
                    }
                }
            }
            iter = newFilter.getPropertyFilterSets().iterator();
            while (iter.hasNext()) {
                PathFilterSet set = iter.next();
                for (String root : mgr.getInternalRegistry().getPackRootPaths()) {
                    if (Text.isDescendantOrEqual(root, set.getRoot())) {
                        iter.remove();
                        break;
                    }
                }
            }

            // re-add all the packages in /etc/packages
            for (Map.Entry<PackageId, String> pkg : subPackages.entrySet()) {
                mgr.getInternalRegistry();
                String path = DEFAULT_PACKAGE_ROOT_PATH + "/" + AbstractPackageRegistry.getRelativeInstallationPath(pkg.getKey()) + ".zip";
                newFilter.add(new PathFilterSet(path));
            }

            return newFilter;
        }

        return null;
    }
}
