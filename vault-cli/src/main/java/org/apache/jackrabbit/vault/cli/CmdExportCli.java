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
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.fs.io.AbstractExporter;
import org.apache.jackrabbit.vault.fs.io.JarExporter;
import org.apache.jackrabbit.vault.fs.io.PlatformExporter;
import org.apache.jackrabbit.vault.util.DefaultProgressListener;
import org.apache.jackrabbit.vault.vlt.VltContext;

/**
 * Implements the 'export' command.
 *
 */
public class CmdExportCli extends AbstractVaultCommand {

    static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ENGLISH);

    private Option optType;
    private Option optPrune;
    private Option optLocalPath;
    private Option optJcrPath;
    private Option optMountpoint;
    private Options options;

    public CmdExportCli() {
        options = new Options();
        options.addOption(OPT_VERBOSE);
        optType = Option.builder("t")
                .longOpt("type")
                .desc("specifies the export type. either 'platform' or 'jar'.")
                .hasArg()
                .build();
        options.addOption(optType);
        optPrune = Option.builder("P")
                .longOpt("prune-missing")
                .desc("specifies if missing local files should be deleted.")
                .build();
        options.addOption(optPrune);
        optJcrPath = Option.builder()
                .argName("jcr-path")
                .desc("the jcr path")
                .hasArg()
                .build();
        options.addOption(optJcrPath);
        optLocalPath = Option.builder()
                .argName("local-path")
                .desc("the local path")
                .hasArg()
                .build();
        options.addOption(optLocalPath);
        optMountpoint = Option.builder()
                .argName("mountpoint")
                .desc("the mountpoint URI")
                .hasArg()
                .build();
        options.addOption(optMountpoint);
    }

    protected void doExecute(VaultFsApp app, CommandLine cl) throws Exception {
        boolean verbose = cl.hasOption(OPT_VERBOSE.getOpt());
        String type = cl.getOptionValue("t", "platform");
        String jcrPath = cl.getOptionValue("jcr-path");
        String localPath = cl.getOptionValue("local-path");
        String root = cl.getOptionValue("mountpoint");
        RepositoryAddress addr = new RepositoryAddress(root);
        // shift arguments
        if (localPath == null) {
            localPath = jcrPath;
            jcrPath = null;
        }
        if (jcrPath == null) {
            jcrPath = "/";
        }
        if (localPath == null) {
            if (jcrPath.equals("/")) {
                localPath = Text.getName(addr.toString());
            } else {
                localPath = Text.getName(jcrPath);
            }
            if (type.equals("jar")) {
                localPath += "-" + FMT.format(Instant.now()) + ".jar";
            }
        }
        File localFile = app.getPlatformFile(localPath, false);
        AbstractExporter exporter = null;
        try {
            VltContext vCtx;
            if (type.equals("platform")) {
                if (!localFile.exists()) {
                    localFile.mkdirs();
                }
                exporter = new PlatformExporter(localFile);
                ((PlatformExporter) exporter).setPruneMissing(cl.hasOption(optPrune.getOpt()));
                vCtx = app.createVaultContext(localFile);
            } else if (type.equals("jar")) {
                exporter = new JarExporter(localFile);
                vCtx = app.createVaultContext(localFile.getParentFile());
            } else {
                throw new Exception("Type " + type + " not supported");
            }
            vCtx.setVerbose(cl.hasOption(OPT_VERBOSE.getOpt()));
            VaultFile vaultFile = vCtx.getFileSystem(addr).getFile(jcrPath);
            if (vaultFile == null) {
                VaultFsApp.log.error("Not such remote file: {}", jcrPath);
                return;
            }
            VaultFsApp.log.info("Exporting {} to {}", vaultFile.getPath(), localFile.getCanonicalPath());
            if (verbose) {
                exporter.setVerbose(new DefaultProgressListener());
            }
            exporter.setNoMetaInf(true);
            exporter.export(vaultFile);
            VaultFsApp.log.info("Exporting done.");
        } finally {
            if (exporter != null) {
                exporter.close();
            }
        }
    }

    public String getShortDescription() {
        return "Export the Vault filesystem";
    }

    public Options getOptions() {
        return options;
    }

    public void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("export", options);
    }
}