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

package org.apache.jackrabbit.vault.cli;

import java.io.File;
import java.io.InputStream;

import javax.jcr.ImportUUIDBehavior;
import javax.jcr.Node;
import javax.jcr.Session;

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.FileArchive;
import org.apache.jackrabbit.vault.fs.io.Importer;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.util.DefaultProgressListener;
import org.apache.jackrabbit.vault.vlt.VltContext;

/**
 * Implements the 'import' command.
 *
 */
public class CmdImportCli extends AbstractVaultCommand {

    private Option optSync;
    private Argument argLocalPath;
    private Argument argJcrPath;
    private Argument argMountpoint;
    private Option optSysView;

    protected void doExecute(VaultFsApp app, CommandLine cl) throws Exception {
        String localPath = (String) cl.getValue(argLocalPath);
        String jcrPath = (String) cl.getValue(argJcrPath);

        boolean verbose = cl.hasOption(OPT_VERBOSE);
        /*
        List excludeList = cl.getValues(optExclude);
        String[] excludes = Constants.EMPTY_STRING_ARRAY;
        if (excludeList != null && excludeList.size() > 0) {
            excludes = (String[]) excludeList.toArray(new String[excludeList.size()]);
        }
        */
        String root = (String) cl.getValue(argMountpoint);
        RepositoryAddress addr = new RepositoryAddress(root);

        if (jcrPath == null) {
            jcrPath = "/";
        }
        File localFile = app.getPlatformFile(localPath, false);

        VltContext vCtx = app.createVaultContext(localFile);
        vCtx.setVerbose(cl.hasOption(OPT_VERBOSE));


        if (cl.hasOption(optSysView)) {
            if (!localFile.isFile()) {
                VaultFsApp.log.error("--sysview specified but local path does not point to a file.");
                return;
            }
            // todo: move to another location
            InputStream ins = FileUtils.openInputStream(localFile);
            try {
                Session session = vCtx.getFileSystem(addr).getAggregateManager().getSession();
                session.getWorkspace().importXML(jcrPath, ins, ImportUUIDBehavior.IMPORT_UUID_COLLISION_REMOVE_EXISTING);
                return;
            } finally {
                IOUtils.closeQuietly(ins);
            }
        }


        VaultFile vaultFile = vCtx.getFileSystem(addr).getFile(jcrPath);

        VaultFsApp.log.info("Importing {} to {}", localFile.getCanonicalPath(), vaultFile.getPath());
        Archive archive;
        if (localFile.isFile()) {
            archive = new ZipArchive(localFile);
        } else {
            if (cl.hasOption(optSync)) {
                VaultFsApp.log.warn("--sync is not supported yet");
            }
            archive = new FileArchive(localFile);
        }
        archive.open(false);
        try {
            Importer importer = new Importer();
            if (verbose) {
                importer.getOptions().setListener(new DefaultProgressListener());
            }
            Session s = vaultFile.getFileSystem().getAggregateManager().getSession();
            Node importRoot = s.getNode(vaultFile.getPath());
            importer.run(archive, importRoot);
        } finally {
            archive.close();
        }
        VaultFsApp.log.info("Importing done.");
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return "import";
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Import a Vault filesystem";
    }


    public String getLongDescription() {
        return "Import the local filesystem (starting at <local-path> to the " +
                "vault filesystem at <uri>. A <jcr-path> can be specified as " +
                "import root. If --sync is specified, the imported files are " +
                "automatically put under vault control.\n\n" +
                "Example:\n" +
                "  vlt import http://localhost:4502/crx . /";
    }

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("import")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(OPT_VERBOSE)
                        .withOption(optSync = new DefaultOptionBuilder()
                                .withShortName("s")
                                .withLongName("sync")
                                .withDescription("put local files under vault control.")
                                .create())
                        .withOption(optSysView = new DefaultOptionBuilder()
                                .withLongName("sysview")
                                .withDescription("specifies that the indicated local file has the sysview format")
                                .create())
                        .withOption(argMountpoint = new ArgumentBuilder()
                                .withName("uri")
                                .withDescription("mountpoint uri")
                                .withMinimum(1)
                                .withMaximum(1)
                                .create())
                        .withOption(argLocalPath = new ArgumentBuilder()
                                .withName("local-path")
                                .withDescription("the local path")
                                .withMinimum(0)
                                .withMaximum(1)
                                .create()
                        )
                        .withOption(argJcrPath = new ArgumentBuilder()
                                .withName("jcr-path")
                                .withDescription("the jcr path")
                                .withMinimum(0)
                                .withMaximum(1)
                                .create()
                        )
                        .create()
                )
                .create();
    }

}