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
package org.apache.jackrabbit.vault.vlt;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Credentials;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.Mounter;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilter;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.VaultFileSystem;
import org.apache.jackrabbit.vault.fs.api.VaultFsConfig;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ExportRoot;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.apache.jackrabbit.vault.util.RepositoryProvider;
import org.apache.jackrabbit.vault.vlt.actions.Action;
import org.apache.jackrabbit.vault.vlt.meta.Ignored;
import org.apache.jackrabbit.vault.vlt.meta.MetaDirectory;
import org.apache.jackrabbit.vault.vlt.meta.xml.zip.ZipMetaDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>VaultContext</code>...
 *
 */
public class VltContext {

    protected static Logger log = LoggerFactory.getLogger(VltContext.class);

    private final File cwd;

    private final RepositoryProvider repProvider;

    private final CredentialsStore credsProvider;

    private final ExportRoot exportRoot;

    private Map<RepositoryAddress, VaultFileSystem> fileSystems
            = new HashMap<RepositoryAddress, VaultFileSystem>();

    private RepositoryAddress mountpoint;

    private boolean verbose;

    private boolean quiet;

    private boolean swallowErrors = true;

    private final PrintStream stdout;

    private String[] defaultFilterRoots = Constants.EMPTY_STRING_ARRAY;

    private String defaultFilter;

    private String fsRoot = "";

    private PathFilter globalIgnored;

    public VltContext(File cwd, File localFile,
            RepositoryProvider repProvider,
            CredentialsStore credsProvider)
                    throws ConfigurationException, IOException {
        this(cwd, localFile, repProvider, credsProvider, System.out);
    }

    public VltContext(File cwd, File localFile,
                        RepositoryProvider repProvider,
                        CredentialsStore credsProvider,
                        PrintStream out)
            throws ConfigurationException, IOException {
        if (!cwd.exists()) {
            throw new FileNotFoundException(cwd.getAbsolutePath());
        }
        if (!localFile.exists()) {
            throw new FileNotFoundException(localFile.getAbsolutePath());
        }
        this.stdout = out;
        this.cwd = cwd;
        this.repProvider = repProvider;
        this.credsProvider = credsProvider;
        ExportRoot er = ExportRoot.findRoot(localFile);
        if (er == null) {
            er = new ExportRoot(localFile);
        }
        this.exportRoot = er;
    }

    public RepositoryAddress getMountpoint() throws VltException {
        if (mountpoint == null) {
            File dir = new File(exportRoot.getJcrRoot(), VltDirectory.META_DIR_NAME);
            MetaDirectory rootMeta = VltContext.createMetaDirectory(dir);
            try {
                String addr = rootMeta.getRepositoryUrl();
                if (addr == null) {
                    throw new VltException("Root directory must provide a repository url file.");
                }
                mountpoint = new RepositoryAddress(addr);
            } catch (IOException e) {
                throw new VltException("error while reading repository address.", e);
            } catch (URISyntaxException e) {
                throw new VltException("Illegal repository address.", e);
            }
        }
        return mountpoint;
    }

    public static MetaDirectory createMetaDirectory(File base) throws VltException {
        //return new FileMetaDir(base);
        try {
            //return new TarMetaDir(base);
            return new ZipMetaDir(base);
        } catch (IOException e) {
            throw new VltException("Error creating meta directory.", e);
        }
    }

    public String getFsRoot() {
        return fsRoot;
    }

    public void setFsRoot(String fsRoot) {
        if (fsRoot == null || fsRoot.equals("/")) {
            this.fsRoot = "";
        } else {
            this.fsRoot = fsRoot;
        }
    }

    public PathFilter getGlobalIgnored() {
        return globalIgnored;
    }

    public void setGlobalIgnored(PathFilter globalIgnored) {
        this.globalIgnored = globalIgnored;
    }

    /**
     * Sets the filesystem root to the aggregate path defined by the entries
     * of the given directory.
     *
     * @param dir the directory
     * @throws VltException if an error occurs
     */
    public void setFsRoot(VltDirectory dir) throws VltException {
        String aPath = dir.getAggregatePath();
        if (aPath != null) {
            RepositoryAddress adr = getMountpoint().resolve(aPath);
            setFsRoot(aPath);
            setMountpoint(adr);
        }
    }

    public void setMountpoint(RepositoryAddress addr) throws VltException {
        mountpoint = addr;
        File dir = new File(exportRoot.getJcrRoot(), VltDirectory.META_DIR_NAME);
        MetaDirectory rootMeta = VltContext.createMetaDirectory(dir);
        try {
            String url = addr.toString();
            if (fsRoot.length() > 0 && url.endsWith(fsRoot)) {
                url = url.substring(0, url.length() - fsRoot.length());
            }
            rootMeta.setRepositoryUrl(url);
        } catch (IOException e) {
            throw new VltException("error while writing repository address.", e);
        }
    }

    public Session login(RepositoryAddress mountpoint) throws RepositoryException {
        Repository rep = repProvider.getRepository(mountpoint);
        Credentials creds = credsProvider.getCredentials(mountpoint);
        Session s = rep.login(creds);
        // hack to store credentials
        credsProvider.storeCredentials(mountpoint, creds);
        return s;
    }

    public VaultFileSystem getFileSystem(RepositoryAddress mountpoint)
            throws VltException {
        VaultFileSystem fs = fileSystems.get(mountpoint);
        if (fs == null) {
            try {
                // check if export root already defines config and filter
                DefaultWorkspaceFilter filter = null;
                VaultFsConfig config = null;
                if (exportRoot != null && exportRoot.getMetaInf() != null) {
                    filter = (DefaultWorkspaceFilter) exportRoot.getMetaInf().getFilter();
                    config = exportRoot.getMetaInf().getConfig();
                }
                if (filter == null && defaultFilterRoots.length > 0) {
                    filter = new DefaultWorkspaceFilter();
                    for (String root: defaultFilterRoots) {
                        filter.add(new PathFilterSet(root));
                    }
                    stdout.printf("Created default filter:%n%s", filter.getSourceAsString());
                }
                if (filter == null && defaultFilter != null) {
                    filter = new DefaultWorkspaceFilter();
                    try {
                        filter.load(new File(defaultFilter));
                    } catch (ConfigurationException e) {
                        throw new VltException("Specified filter is not valid.", e);
                    }
                }
                // get .vltignore files
                if (exportRoot != null && filter != null) {
                    if (globalIgnored == null) {
                        globalIgnored = new Ignored(this, cwd);
                    }
                    filter.setGlobalIgnored(globalIgnored);
                }
                // override any import mode defined in the filter as this is not expected when committing files (GRANITE-XYZ)
                if (filter != null) {
                    filter.setImportMode(ImportMode.REPLACE);
                }

                Repository rep = repProvider.getRepository(mountpoint);
                Credentials creds = credsProvider.getCredentials(mountpoint);
                fs = Mounter.mount(config, filter, rep, creds, mountpoint, fsRoot);
                // hack to store credentials
                credsProvider.storeCredentials(mountpoint, creds);

            } catch (RepositoryException e) {
                throw new VltException("Unable to mount filesystem", e);
            } catch (IOException e) {
                throw new VltException("Unable to mount filesystem", e);
            }
            fileSystems.put(mountpoint, fs);
        }
        return fs;
    }

    public ExportRoot getExportRoot() {
        return exportRoot;
    }

    public MetaInf getMetaInf() {
        return exportRoot.isValid() ? exportRoot.getMetaInf() : null;
    }

    public String[] getDefaultFilterRoots() {
        return defaultFilterRoots;
    }

    public void setDefaultFilterRoots(String[] defaultFilterRoots) {
        this.defaultFilterRoots = defaultFilterRoots;
    }

    public String getDefaultFilter() {
        return defaultFilter;
    }

    public void setDefaultFilter(String defaultFilter) {
        this.defaultFilter = defaultFilter;
    }

    public void close() {
        for (RepositoryAddress addr: fileSystems.keySet()) {
            VaultFileSystem fs = fileSystems.get(addr);
            try {
                fs.unmount();
            } catch (RepositoryException e) {
                log.warn("Error while unmounting fs.", e);
            }
        }
        fileSystems.clear();
    }

    public boolean execute(Action action) throws VltException {
        try {
            action.run(this);
        } catch (VltException e) {
            if (swallowErrors && e.isUserError()) {
                printError(e);
                return false;
            } else {
                throw e;
            }
        }
        return true;
    }

    public String getCwdRelativePath(String path) {
        return PathUtil.getRelativeFilePath(cwd.getPath(), path);
    }

    public VltException error(String path, String msg) {
        path = getCwdRelativePath(path);
        return new VltException(path, true, msg, null);
    }

    public VltException exception(String path, String msg, Throwable cause) {
        path = getCwdRelativePath(path);
        return new VltException(path, false, msg, cause);
    }

    public void printAction(VltFile file, FileAction action) {
        printAction(file.getPath(), action, file.getContentType());
    }

    public void printAction(String path, FileAction action, String contentType) {
        if (!quiet && (verbose || action != FileAction.VOID)) {
            path = getCwdRelativePath(path);
            if (action == FileAction.ADDED && contentType != null) {
                stdout.printf("%s %s (%s)%n", action.letter, path, contentType);
            } else {
                stdout.printf("%s %s%n", action.letter, path);
            }
            stdout.flush();
        }
    }

    public void printError(VltException e) {
        stdout.println(e.getMessage());
        stdout.flush();
    }

    public void printMessage(VltFile file, String msg) {
        if (!quiet) {
            String path = getCwdRelativePath(file.getPath());
            stdout.printf("%s %s%n", path, msg);
            stdout.flush();
        }
    }
    
    public void printMessage(String msg, VltFile file) {
        if (!quiet) {
            String path = getCwdRelativePath(file.getPath());
            stdout.printf("%s %s%n", msg, path);
            stdout.flush();
        }
    }

    public void printMessage(String msg) {
        if (!quiet) {
            stdout.println(msg);
            stdout.flush();
        }
    }

    public void printStatus(VltFile file)
            throws VltException {
        String path = getCwdRelativePath(file.getPath());
        VltFile.State state = file.getStatus();
        if (quiet && state == VltFile.State.UNKNOWN) {
            return;
        }
        if (verbose || state != VltFile.State.CLEAN) {
            if (state == VltFile.State.ADDED && file.getContentType() != null) {
                stdout.printf("%s %s (%s)%n", state.letter, path, file.getContentType());
            } else {
                stdout.printf("%s %s%n", state.letter, path);
            }
            stdout.flush();
        }
    }

    public void printRemoteStatus(VltFile file, FileAction action)
            throws VltException {
        String path = getCwdRelativePath(file.getPath());
        VltFile.State state = file.getStatus();
        if (quiet && state == VltFile.State.UNKNOWN && action == FileAction.VOID) {
            return;
        }
        if (verbose || state != VltFile.State.CLEAN || action != FileAction.VOID) {
            stdout.printf("%s%s %s%n", state.letter, action.letter, path);
            stdout.flush();
        }
    }

    public PrintStream getStdout() {
        return stdout;
    }

    public File getCwd() {
        return cwd;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isQuiet() {
        return quiet;
    }

    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

}