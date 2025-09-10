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
import javax.jcr.Session;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
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
    private Option optSysView;
    private Option optLocalPath;
    private Option optJcrPath;
    private Option optMountpoint;
    private Options options;

    public CmdImportCli() {
        options = new Options();
        options.addOption(OPT_VERBOSE);
        optSync = Option.builder()
                .longOpt("sync")
                .desc("automatically put imported files under vault control")
                .build();
        options.addOption(optSync);
        optSysView = Option.builder()
                .longOpt("sysview")
                .desc("import as sysview xml")
                .build();
        options.addOption(optSysView);
        optLocalPath = Option.builder()
                .argName("local-path")
                .desc("the local path")
                .hasArg()
                .required()
                .build();
        options.addOption(optLocalPath);
        optJcrPath = Option.builder()
                .argName("jcr-path")
                .desc("the jcr path")
                .hasArg()
                .build();
        options.addOption(optJcrPath);
        optMountpoint = Option.builder()
                .argName("mountpoint")
                .desc("the mountpoint URI")
                .hasArg()
                .required()
                .build();
        options.addOption(optMountpoint);
    }

    protected void doExecute(VaultFsApp app, CommandLine cl) throws Exception {
        String localPath = cl.getOptionValue(optLocalPath.getOpt());
        String jcrPath = cl.getOptionValue(optJcrPath.getOpt());
        String root = cl.getOptionValue(optMountpoint.getOpt());
        boolean verbose = cl.hasOption(OPT_VERBOSE.getOpt());
        RepositoryAddress addr = new RepositoryAddress(root);
        if (jcrPath == null) {
            jcrPath = "/";
        }
        File localFile = app.getPlatformFile(localPath, false);
        VltContext vCtx = app.createVaultContext(localFile);
        vCtx.setVerbose(verbose);
        if (cl.hasOption(optSysView.getOpt())) {
            if (!localFile.isFile()) {
                VaultFsApp.log.error("--sysview specified but local path does not point to a file.");
                return;
            }
            try (InputStream ins = org.apache.commons.io.FileUtils.openInputStream(localFile)) {
                Session session = vCtx.getFileSystem(addr).getAggregateManager().getSession();
                session.getWorkspace().importXML(jcrPath, ins, ImportUUIDBehavior.IMPORT_UUID_COLLISION_REMOVE_EXISTING);
                return;
            }
        }
        VaultFile vaultFile = vCtx.getFileSystem(addr).getFile(jcrPath);
        VaultFsApp.log.info("Importing {} to {}", localFile.getCanonicalPath(), vaultFile.getPath());
        Archive archive = null;
        try {
            if (localFile.isFile()) {
                archive = new ZipArchive(localFile);
            } else {
                if (cl.hasOption(optSync.getOpt())) {
                    VaultFsApp.log.warn("--sync is not supported yet");
                }
                archive = new FileArchive(localFile);
            }
            archive.open(false);
            Importer importer = new Importer();
            if (verbose) {
                importer.getOptions().setListener(new DefaultProgressListener());
            }
            Session s = vaultFile.getFileSystem().getAggregateManager().getSession();
            importer.run(archive, s, vaultFile.getPath());
        } finally {
            if (archive != null) {
                archive.close();
            }
        }
        VaultFsApp.log.info("Importing done.");
    }

    public String getShortDescription() {
        return "Import a Vault filesystem";
    }

    public Options getOptions() {
        return options;
    }

    public void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("import", options);
    }
}