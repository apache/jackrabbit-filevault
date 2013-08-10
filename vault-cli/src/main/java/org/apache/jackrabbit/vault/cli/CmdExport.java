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

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
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
    private Argument argLocalPath;
    private Argument argJcrPath;

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        String localPath = (String) cl.getValue(argLocalPath);
        String jcrPath = (String) cl.getValue(argJcrPath);
        boolean verbose = cl.hasOption(OPT_VERBOSE);
        String type = (String) cl.getValue(optType, "platform");
        VaultFile vaultFile = ctx.getVaultFsApp().getVaultFile(jcrPath, true);
        if (localPath == null) {
            localPath = vaultFile.getName();
        }
        File localFile = ctx.getVaultFsApp().getPlatformFile(localPath, false);

        AbstractExporter exporter;
        if (type.equals("platform")) {
            if (!localFile.exists()) {
                localFile.mkdirs();
            }
            exporter = new PlatformExporter(localFile);
            ((PlatformExporter) exporter).setPruneMissing(cl.hasOption(optPrune));
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
        exporter.export(vaultFile);
        VaultFsApp.log.info("Exporting done.");
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

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("export")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(OPT_VERBOSE)
                        .withOption(optType = new DefaultOptionBuilder()
                                .withShortName("t")
                                .withDescription("specifies the export type. either 'platform' or 'jar'.")
                                .withArgument(new ArgumentBuilder()
                                        .withMinimum(0)
                                        .withMaximum(1)
                                        .create())
                                .create())
                        .withOption(optPrune = new DefaultOptionBuilder()
                                .withShortName("P")
                                .withLongName("prune-missing")
                                .withDescription("specifies if missing local files should be deleted.")
                                .create())
                        .withOption(argJcrPath = new ArgumentBuilder()
                                .withName("jcr-path")
                                .withDescription("the jcr path")
                                .withMinimum(0)
                                .withMaximum(1)
                                .create()
                        )
                        .withOption(argLocalPath = new ArgumentBuilder()
                                .withName("local-path")
                                .withDescription("the local path")
                                .withMinimum(0)
                                .withMaximum(1)
                                .create()
                        )
                        .create()
                )
                .create();
    }
}