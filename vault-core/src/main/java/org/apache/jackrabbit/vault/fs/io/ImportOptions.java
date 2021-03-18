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
import org.apache.jackrabbit.vault.packaging.DependencyHandling;
import org.jetbrains.annotations.Nullable;

/**
 * Option that control the package import.
 */
public class ImportOptions {

    private Boolean strict;

    private ProgressTrackerListener listener;

    private String patchParentPath = "/var/crxpatches/";

    private File patchDirectory;

    private boolean patchKeepInRepo = true;

    private boolean nonRecursive = false;

    private boolean dryRun;

    private int autoSave = -1;

    private AccessControlHandling acHandling = null;

    private AccessControlHandling cugHandling = null;

    private ImportMode importMode;

    private Pattern cndPattern = Pattern.compile("^/(apps|libs)/([^/]+/){1,2}nodetypes/.+\\.cnd$");

    private WorkspaceFilter filter = null;

    private ClassLoader hookClassLoader;

    private PathMapping pathMapping = null;

    private DependencyHandling dependencyHandling = null;

    /**
     * Default constructor.
     */
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
            cugHandling = base.cugHandling;
            importMode = base.importMode;
            cndPattern = base.cndPattern;
            filter = base.filter;
            hookClassLoader = base.hookClassLoader;
            pathMapping = base.pathMapping;
            dependencyHandling = base.dependencyHandling;
        }
    }

    /**
     * Creates a copy of this import options.
     * @return a copy of this.
     */
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
        ret.cugHandling = cugHandling;
        ret.importMode = importMode;
        ret.cndPattern = cndPattern;
        ret.filter = filter;
        ret.hookClassLoader = hookClassLoader;
        ret.pathMapping = pathMapping;
        ret.dependencyHandling = dependencyHandling;
        return ret;
    }

    public boolean isStrict(boolean isStrictByDefault) {
        if (strict == null) {
            return isStrictByDefault;
        } else {
            return strict;
        }
    }

    /**
     * Returns the 'strict' flag.
     * @return the 'strict' flag or {@code null} in case this is not set
     * @deprecated Use {@link #isStrict(boolean)} instead.
     */
    @Deprecated
    public boolean isStrict() {
        if (strict == null) {
            return false;
        } else {
            return strict;
        }
    }

    /**
     * Sets the 'strict' flag.
     * @param strict the flag
     */
    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    /**
     * Returns the progress tracker listener.
     * @return the progress tracker listener.
     */
    public ProgressTrackerListener getListener() {
        return listener;
    }

    /**
     * Sets the progress tracker listener that receives messages during package installation.
     * @param listener The listener
     */
    public void setListener(ProgressTrackerListener listener) {
        this.listener = listener;
    }

    /**
     * Returns the patch parent path
     * @return the patch parent path
     */
    public String getPatchParentPath() {
        return patchParentPath;
    }

    /**
     * Sets the parent path of the patch node.
     * @param patchParentPath the path
     */
    public void setPatchParentPath(String patchParentPath) {
        this.patchParentPath = patchParentPath;
    }

    /**
     * Returns the patch directory
     * @return the patch directory
     */
    public File getPatchDirectory() {
        return patchDirectory;
    }

    /**
     * Sets the patch directory. The nt:file nodes that are placed below the {@link #getPatchParentPath()} will be
     * copied into this directory during extraction.
     * @param patchDirectory The directory
     * @throws IOException if an i/o error occurrs during obtaining the canonical file of this directory.
     */
    public void setPatchDirectory(File patchDirectory) throws IOException {
        this.patchDirectory = patchDirectory == null
                ? null
                : patchDirectory.getCanonicalFile();
    }

    /**
     * Returns the 'patch-keep-in-repo' flag.
     * @return the 'patch-keep-in-repo' flag.
     */
    public boolean isPatchKeepInRepo() {
        return patchKeepInRepo;
    }

    /**
     * Sets the flag if patches should be kept in the repository after there were copied to the disk.
     * @param patchKeepInRepo the flag
     */
    public void setPatchKeepInRepo(boolean patchKeepInRepo) {
        this.patchKeepInRepo = patchKeepInRepo;
    }

    /**
     * Returns the default access control handling.
     * @return the default access control handling.
     */
    public AccessControlHandling getAccessControlHandling() {
        return acHandling;
    }

    /**
     * Sets the access control handling.
     * @param acHandling the ACL handling.
     */
    public void setAccessControlHandling(AccessControlHandling acHandling) {
        this.acHandling = acHandling;
    }

    /**
     * Returns closed user group handling.
     * @return CUG handling value. <code>null</code> value indicates that CUG
     * handling is controlled by acHandling value which maintains backwards compatibility.
     */
    public AccessControlHandling getCugHandling() {
        return cugHandling;
    }

    /**
     * Sets closed user group handling. For backwards compatibility, when cugHandling is set to
     * null <code>null</code> then acHandling is used is used to control handling of CUG nodes.
     * @param cugHandling the CUG handling.
     */
    public void setCugHandling(AccessControlHandling cugHandling) {
        this.cugHandling = cugHandling;
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
     * Sets the flag to ignore sub packages.
     * @param nonRecursive {@code true} to set non recursive
     * @see #isNonRecursive()
     */
    public void setNonRecursive(boolean nonRecursive) {
        this.nonRecursive = nonRecursive;
    }

    /**
     * Returns the CND pattern
     * @return the CND pattern
     */
    public Pattern getCndPattern() {
        return cndPattern;
    }

    /**
     * Sets the CND file pattern.
     * @param cndPattern the cnd pattern
     * @throws PatternSyntaxException If the pattern is not valid
     */
    public void setCndPattern(String cndPattern) throws PatternSyntaxException {
        this.cndPattern = Pattern.compile(cndPattern);
    }

    /**
     * Returns the dry run flag.
     * @return the dry run flag.
     * @since 2.2.14
     */
    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * Sets the dry run flag.
     * @param dryRun the dry run flag.
     * @since 2.2.14
     */
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * Sets the auto-save threshold. See {@link AutoSave}
     * @param threshold the threshold in number of nodes.
     * @since 2.2.16
     */
    public void setAutoSaveThreshold(int threshold) {
        this.autoSave = threshold;
    }

    /**
     * Returns the auto-save threshold.
     * @return the auto-save threshold.
     * @since 2.2.16
     */
    public int getAutoSaveThreshold() {
        return autoSave;
    }

    /**
     * Returns the import mode.
     * @return the import mode.
     * @since 2.3
     */
    public ImportMode getImportMode() {
        return importMode;
    }

    /**
     * Sets the default import mode.
     * @param importMode The import mode.
     * @since 2.3
     */
    public void setImportMode(ImportMode importMode) {
        this.importMode = importMode;
    }

    /**
     * Returns the default workspace filter.
     * @return the default workspace filter.
     * @since 2.3.20
     */
    public WorkspaceFilter getFilter() {
        return filter;
    }

    /**
     * Sets the default workspace filter.
     * @param filter the filter
     * @since 2.3.20
     */
    public void setFilter(WorkspaceFilter filter) {
        this.filter = filter;
    }

    /**
     * Returns the hook class loader.
     * @return the hook class loader.
     * @since 2.3.22
     */
    public ClassLoader getHookClassLoader() {
        return hookClassLoader;
    }

    /**
     * Sets the hook class loader.
     * @param hookClassLoader the class loader
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
     * @param pathMapping The path mapping
     * @see #getPathMapping()
     * @since 3.1.14
     */
    public void setPathMapping(PathMapping pathMapping) {
        this.pathMapping = pathMapping;
    }

    /**
     * Defines how package dependencies affect package installation and un-installation.
     * @return the dependency handling.
     */
    public DependencyHandling getDependencyHandling() {
        return dependencyHandling;
    }

    /**
     * Sets the dependency handling.
     * @param dependencyHandling the dependency handling.
     * @see #getDependencyHandling()
     * @since 3.1.32
     */
    public void setDependencyHandling(DependencyHandling dependencyHandling) {
        this.dependencyHandling = dependencyHandling;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((acHandling == null) ? 0 : acHandling.hashCode());
        result = prime * result + autoSave;
        result = prime * result + ((cndPattern == null) ? 0 : cndPattern.hashCode());
        result = prime * result + ((cugHandling == null) ? 0 : cugHandling.hashCode());
        result = prime * result + ((dependencyHandling == null) ? 0 : dependencyHandling.hashCode());
        result = prime * result + (dryRun ? 1231 : 1237);
        result = prime * result + ((filter == null) ? 0 : filter.hashCode());
        result = prime * result + ((hookClassLoader == null) ? 0 : hookClassLoader.hashCode());
        result = prime * result + ((importMode == null) ? 0 : importMode.hashCode());
        result = prime * result + ((listener == null) ? 0 : listener.hashCode());
        result = prime * result + (nonRecursive ? 1231 : 1237);
        result = prime * result + ((patchDirectory == null) ? 0 : patchDirectory.hashCode());
        result = prime * result + (patchKeepInRepo ? 1231 : 1237);
        result = prime * result + ((patchParentPath == null) ? 0 : patchParentPath.hashCode());
        result = prime * result + ((pathMapping == null) ? 0 : pathMapping.hashCode());
        result = prime * result + (strict ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ImportOptions other = (ImportOptions) obj;
        if (acHandling != other.acHandling)
            return false;
        if (autoSave != other.autoSave)
            return false;
        if (cndPattern == null) {
            if (other.cndPattern != null)
                return false;
        } else if (!cndPattern.pattern().equals(other.cndPattern.pattern()))
            return false;
        if (cugHandling != other.cugHandling)
            return false;
        if (dependencyHandling != other.dependencyHandling)
            return false;
        if (dryRun != other.dryRun)
            return false;
        if (filter == null) {
            if (other.filter != null)
                return false;
        } else if (!filter.equals(other.filter))
            return false;
        if (hookClassLoader == null) {
            if (other.hookClassLoader != null)
                return false;
        } else if (!hookClassLoader.equals(other.hookClassLoader))
            return false;
        if (importMode != other.importMode)
            return false;
        if (listener == null) {
            if (other.listener != null)
                return false;
        } else if (!listener.equals(other.listener))
            return false;
        if (nonRecursive != other.nonRecursive)
            return false;
        if (patchDirectory == null) {
            if (other.patchDirectory != null)
                return false;
        } else if (!patchDirectory.equals(other.patchDirectory))
            return false;
        if (patchKeepInRepo != other.patchKeepInRepo)
            return false;
        if (patchParentPath == null) {
            if (other.patchParentPath != null)
                return false;
        } else if (!patchParentPath.equals(other.patchParentPath))
            return false;
        if (pathMapping == null) {
            if (other.pathMapping != null)
                return false;
        } else if (!pathMapping.equals(other.pathMapping))
            return false;
        if (strict != other.strict)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "ImportOptions [strict=" + strict + ", " + (listener != null ? "listener=" + listener + ", " : "")
                + (patchParentPath != null ? "patchParentPath=" + patchParentPath + ", " : "")
                + (patchDirectory != null ? "patchDirectory=" + patchDirectory + ", " : "") + "patchKeepInRepo=" + patchKeepInRepo
                + ", nonRecursive=" + nonRecursive + ", dryRun=" + dryRun + ", autoSave=" + autoSave + ", "
                + (acHandling != null ? "acHandling=" + acHandling + ", " : "")
                + (cugHandling != null ? "cugHandling=" + cugHandling + ", " : "")
                + (importMode != null ? "importMode=" + importMode + ", " : "")
                + (cndPattern != null ? "cndPattern=" + cndPattern + ", " : "") + (filter != null ? "filter=" + filter + ", " : "")
                + (hookClassLoader != null ? "hookClassLoader=" + hookClassLoader + ", " : "")
                + (pathMapping != null ? "pathMapping=" + pathMapping + ", " : "")
                + (dependencyHandling != null ? "dependencyHandling=" + dependencyHandling : "") + "]";
    }
    
    
}