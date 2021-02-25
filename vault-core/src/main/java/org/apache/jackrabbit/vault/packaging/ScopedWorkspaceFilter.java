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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilter;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.PathMapping;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.util.Text;
import org.w3c.dom.Element;

/**
 * Workspace filter wrapper that limits the filter in- or outside certain paths.
 * This is mostly used to limit the scope of mixed content packages to either application or content.
 */
public class ScopedWorkspaceFilter extends DefaultWorkspaceFilter {

    private static final String[] APP_ROOTS = {"/apps", "/libs" };

    private final DefaultWorkspaceFilter base;

    private final boolean allow;

    private final String[] roots;

    private final List<PathFilterSet> nodesFilterSets;

    private final List<PathFilterSet> propsFilterSets;

    public ScopedWorkspaceFilter(DefaultWorkspaceFilter base, boolean allow, String[] roots) {
        this.base = base;
        this.allow = allow;
        this.roots = roots;

        List<PathFilterSet> nodesFilterSets = new ArrayList<>();
        List<PathFilterSet> propsFilterSets = new ArrayList<>();
        for (PathFilterSet set: base.getFilterSets()) {
            String root = set.getRoot();
            if ("/".equals(root) || match(root)) {
                nodesFilterSets.add(set);
            }
        }
        for (PathFilterSet set: base.getPropertyFilterSets()) {
            String root = set.getRoot();
            if ("/".equals(root) || match(root)) {
                propsFilterSets.add(set);
            }
        }
        this.nodesFilterSets = Collections.unmodifiableList(nodesFilterSets);
        this.propsFilterSets = Collections.unmodifiableList(propsFilterSets);
    }

    private boolean match(String path) {
        if (allow) {
            for (String root: roots) {
                if (Text.isDescendantOrEqual(root, path)) {
                    return true;
                }
            }
            return false;
        }

        for (String root: roots) {
            if (Text.isDescendantOrEqual(root, path)) {
                return false;
            }
        }
        return true;
    }

    public static ScopedWorkspaceFilter createApplicationScoped(DefaultWorkspaceFilter base) {
        return new ScopedWorkspaceFilter(base, true, APP_ROOTS);
    }

    public static ScopedWorkspaceFilter createContentScoped(DefaultWorkspaceFilter base) {
        return new ScopedWorkspaceFilter(base, false, APP_ROOTS);
    }

    @Override
    public void add(PathFilterSet set) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(PathFilterSet nodeFilter, PathFilterSet propFilter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addPropertyFilterSet(PathFilterSet set) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<PathFilterSet> getFilterSets() {
        return nodesFilterSets;
    }

    @Override
    public List<PathFilterSet> getPropertyFilterSets() {
        return propsFilterSets;
    }

    @Override
    public PathFilterSet getCoveringFilterSet(String path) {
        if (!match(path)) {
            return null;
        }
        return base.getCoveringFilterSet(path);
    }

    @Override
    public ImportMode getImportMode(String path) {
        return base.getImportMode(path);
    }

    @Override
    public void setImportMode(ImportMode importMode) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(String path) {
        return match(path) && base.contains(path);
    }

    @Override
    public boolean covers(String path) {
        return match(path) && base.covers(path);
    }

    @Override
    public boolean isAncestor(String path) {
        for (PathFilterSet set: nodesFilterSets) {
            if (set.isAncestor(path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isGloballyIgnored(String path) {
        return base.isGloballyIgnored(path);
    }

    @Override
    public WorkspaceFilter translate(PathMapping mapping) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void load(File file) throws IOException, ConfigurationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getSource() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSourceAsString() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void load(InputStream in) throws IOException, ConfigurationException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected PathFilter readFilter(Element elem) throws ConfigurationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void dump(DumpContext ctx, boolean isLast) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resetSource() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setGlobalIgnored(PathFilter ignored) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void dumpCoverage(Node rootNode, ProgressTrackerListener listener) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void dumpCoverage(Session session, ProgressTrackerListener listener, boolean skipJcrContent) throws RepositoryException {
        throw new UnsupportedOperationException();
    }
}
