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

package org.apache.jackrabbit.vault.fs.io;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathMapping;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;

/**
 * Option that control the package import.
 */
public class ImportOptions {

    private boolean strict;

    private ProgressTrackerListener listener;

    private String patchParentPath = "/var/crxpatches/";

    private File patchDirectory;

    private boolean patchKeepInRepo = true;

    private boolean nonRecursive = false;

    private boolean dryRun;

    private int autoSave = -1;

    private AccessControlHandling acHandling = null;

    private ImportMode importMode;

    private Pattern cndPattern = Pattern.compile("^/(apps|libs)/([^/]+/){1,2}nodetypes/.+\\.cnd$");

    private WorkspaceFilter filter = null;

    private ClassLoader hookClassLoader;

    private PathMapping pathMapping = null;

    public ImportOptions() {
        // default constructor.
    }

    /**
     * @deprecated use {@link #copy()} instead.
     * @param base base options
     */
    @Deprecated
    public ImportOptions(ImportOptions base) {
        if (base != null) {
            strict = base.strict;
            listener = base.listener;
            patchParentPath = base.patchParentPath;
            patchDirectory = base.patchDirectory;
            patchKeepInRepo = base.patchKeepInRepo;
            nonRecursive = base.nonRecursive;
            dryRun = base.dryRun;
            autoSave = base.autoSave;
            acHandling = base.acHandling;
            importMode = base.importMode;
            cndPattern = base.cndPattern;
            filter = base.filter;
            hookClassLoader = base.hookClassLoader;
            pathMapping = base.pathMapping;
        }
    }

    public ImportOptions copy() {
        ImportOptions ret = new ImportOptions();
        ret.strict = strict;
        ret.listener = listener;
        ret.patchParentPath = patchParentPath;
        ret.patchDirectory = patchDirectory;
        ret.patchKeepInRepo = patchKeepInRepo;
        ret.nonRecursive = nonRecursive;
        ret.dryRun = dryRun;
        ret.autoSave = autoSave;
        ret.acHandling = acHandling;
        ret.importMode = importMode;
        ret.cndPattern = cndPattern;
        ret.filter = filter;
        ret.hookClassLoader = hookClassLoader;
        ret.pathMapping = pathMapping;
        return ret;
    }

    public boolean isStrict() {
        return strict;
    }

    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    public ProgressTrackerListener getListener() {
        return listener;
    }

    public void setListener(ProgressTrackerListener listener) {
        this.listener = listener;
    }

    public String getPatchParentPath() {
        return patchParentPath;
    }

    public void setPatchParentPath(String patchParentPath) {
        this.patchParentPath = patchParentPath;
    }

    public File getPatchDirectory() {
        return patchDirectory;
    }

    public void setPatchDirectory(File patchDirectory) throws IOException {
        this.patchDirectory = patchDirectory == null
                ? null
                : patchDirectory.getCanonicalFile();
    }

    public boolean isPatchKeepInRepo() {
        return patchKeepInRepo;
    }

    public void setPatchKeepInRepo(boolean patchKeepInRepo) {
        this.patchKeepInRepo = patchKeepInRepo;
    }

    public AccessControlHandling getAccessControlHandling() {
        return acHandling;
    }

    public void setAccessControlHandling(AccessControlHandling acHandling) {
        this.acHandling = acHandling;
    }

    /**
     * Defines the package installation should recursively install sub packages. Note that if this flag is enabled,
     * the {@link org.apache.jackrabbit.vault.packaging.SubPackageHandling} configuration has no effect, as sub packages
     * are not evaluated at all.
     *
     * @return {@code true} if package installation should not install sub packages.
     */
    public boolean isNonRecursive() {
        return nonRecursive;
    }

    /**
     * @see #isNonRecursive()
     */
    public void setNonRecursive(boolean nonRecursive) {
        this.nonRecursive = nonRecursive;
    }

    public Pattern getCndPattern() {
        return cndPattern;
    }

    public void setCndPattern(String cndPattern) throws PatternSyntaxException {
        this.cndPattern = Pattern.compile(cndPattern);
    }

    /**
     * @since 2.2.14
     */
    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * @since 2.2.14
     */
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * @since 2.2.16
     */
    public void setAutoSaveThreshold(int threshold) {
        this.autoSave = threshold;
    }

    /**
     * @since 2.2.16
     */
    public int getAutoSaveThreshold() {
        return autoSave;
    }

    /**
     * @since 2.3
     */
    public ImportMode getImportMode() {
        return importMode;
    }

    /**
     * @since 2.3
     */
    public void setImportMode(ImportMode importMode) {
        this.importMode = importMode;
    }

    /**
     * @since 2.3.20
     */
    public WorkspaceFilter getFilter() {
        return filter;
    }

    /**
     * @since 2.3.20
     */
    public void setFilter(WorkspaceFilter filter) {
        this.filter = filter;
    }

    /**
     * @since 2.3.22
     */
    public ClassLoader getHookClassLoader() {
        return hookClassLoader;
    }

    /**
     * @since 2.3.22
     */
    public void setHookClassLoader(ClassLoader hookClassLoader) {
        this.hookClassLoader = hookClassLoader;
    }

    /**
     * Defines a path mapping that is applied to the incoming package paths and filter when installing the package.
     *
     * @since 3.1.14
     * @return {@code null} if no path mapping is defined.
     */
    public PathMapping getPathMapping() {
        return pathMapping;
    }

    /**
     * Sets the path mapping
     * @see #getPathMapping()
     * @since 3.1.14
     */
    public void setPathMapping(PathMapping pathMapping) {
        this.pathMapping = pathMapping;
    }
}