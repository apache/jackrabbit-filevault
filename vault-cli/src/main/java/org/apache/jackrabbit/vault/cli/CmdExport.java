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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.fs.io.AbstractExporter;
import org.apache.jackrabbit.vault.fs.io.JarExporter;
import org.apache.jackrabbit.vault.fs.io.PlatformExporter;
import org.apache.jackrabbit.vault.util.DefaultProgressListener;

/**
 * Implements the 'export' command.
 *
 */
public class CmdExport extends AbstractJcrFsCommand {

    private Option optType;
    private Option optPrune;
    private Option optLocalPath;
    private Option optJcrPath;
    private Options options;

    public CmdExport() {
        options = new Options();
        options.addOption(OPT_VERBOSE);
        optType = Option.builder("t")
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
    }

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        String localPath = cl.getOptionValue("local-path");
        String jcrPath = cl.getOptionValue("jcr-path");
        boolean verbose = cl.hasOption(OPT_VERBOSE.getOpt());
        String type = cl.getOptionValue("t", "platform");
        VaultFile vaultFile = ctx.getVaultFsApp().getVaultFile(jcrPath, true);
        if (localPath == null) {
            localPath = vaultFile.getName();
        }
        File localFile = ctx.getVaultFsApp().getPlatformFile(localPath, false);
        AbstractExporter exporter = null;
        try {
            if (type.equals("platform")) {
                if (!localFile.exists()) {
                    localFile.mkdirs();
                }
                exporter = new PlatformExporter(localFile);
                ((PlatformExporter) exporter).setPruneMissing(cl.hasOption(optPrune.getOpt()));
            } else if (type.equals("jar")) {
                exporter = new JarExporter(localFile);
            } else {
                throw new Exception("Type " + type + " not supported");
            }
            if (jcrPath == null || !jcrPath.startsWith("/")) {
                exporter.setRelativePaths(true);
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

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Export the Vault filesystem";
    }


    public String getLongDescription() {
        return  "Export the Vault filesystem (starting at <jcr-path> to the " +
                "local filesystem at <local-path>. Both paths can be relative " +
                "to their respective CWDs.";
    }

    public Options getOptions() {
        return options;
    }

    public void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("export", options);
    }
}