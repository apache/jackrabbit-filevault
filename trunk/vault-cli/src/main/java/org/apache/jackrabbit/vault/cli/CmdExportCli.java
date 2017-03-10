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
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.fs.io.AbstractExporter;
import org.apache.jackrabbit.vault.fs.io.JarExporter;
import org.apache.jackrabbit.vault.fs.io.PlatformExporter;
import org.apache.jackrabbit.vault.util.DefaultProgressListener;
import org.apache.jackrabbit.vault.util.Text;
import org.apache.jackrabbit.vault.vlt.VltContext;

/**
 * Implements the 'export' command.
 *
 */
public class CmdExportCli extends AbstractVaultCommand {

    static final SimpleDateFormat FMT = new SimpleDateFormat("yyyyMMddHHmmss");

    private Option optType;
    private Option optPrune;
    private Argument argLocalPath;
    private Argument argJcrPath;
    private Argument argMountpoint;


    protected void doExecute(VaultFsApp app, CommandLine cl) throws Exception {
        boolean verbose = cl.hasOption(OPT_VERBOSE);
        String type = (String) cl.getValue(optType, "platform");

        String jcrPath = (String) cl.getValue(argJcrPath);
        String localPath = (String) cl.getValue(argLocalPath);
        String root = (String) cl.getValue(argMountpoint);
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
            if (jcrPath .equals("/")) {
                localPath = Text.getName(addr.toString());
            } else {
                localPath = Text.getName(jcrPath);
            }
            if (type.equals("jar")) {
                localPath += "-" + FMT.format(new Date()) + ".jar";
            } else {

            }
        }
        File localFile = app.getPlatformFile(localPath, false);

        AbstractExporter exporter;
        VltContext vCtx;
        if (type.equals("platform")) {
            if (!localFile.exists()) {
                localFile.mkdirs();
            }
            exporter = new PlatformExporter(localFile);
            ((PlatformExporter) exporter).setPruneMissing(cl.hasOption(optPrune));
            vCtx = app.createVaultContext(localFile);
        } else if (type.equals("jar")) {
            exporter = new JarExporter(localFile);
            vCtx = app.createVaultContext(localFile.getParentFile());
        } else {
            throw new Exception("Type " + type + " not supported");
        }

        vCtx.setVerbose(cl.hasOption(OPT_VERBOSE));
        VaultFile vaultFile = vCtx.getFileSystem(addr).getFile(jcrPath);
        if (vaultFile == null) {
            VaultFsApp.log.error("Not such remote file: {}", jcrPath);
            return;
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
        return  "Export the Vault filesystem mounted at <uri> to the " +
                "local filesystem at <local-path>. An optional <jcr-path> can be " +
                "specified in order to export just a sub tree.\n\n" +
                "Example:\n" +
                "  vlt export http://localhost:4502/crx /apps/geometrixx myproject";
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
                                .withLongName("type")
                                .withDescription("specifies the export type. either 'platform' or 'jar'.")
                                .withArgument(new ArgumentBuilder()
                                        .withMinimum(0)
                                        .withMaximum(1)
                                        .create())
                                .create())
                        .withOption(optPrune = new DefaultOptionBuilder()
                                .withShortName("p")
                                .withLongName("prune-missing")
                                .withDescription("specifies if missing local files should be deleted.")
                                .create())
                        .withOption(argMountpoint = new ArgumentBuilder()
                                .withName("uri")
                                .withDescription("mountpoint uri")
                                .withMinimum(1)
                                .withMaximum(1)
                                .create())
                        .withOption(argJcrPath = new ArgumentBuilder()
                                .withName("jcr-path")
                                .withDescription("the jcr path")
                                .withMinimum(0)
                                .withMaximum(1)
                                .create())
                        .withOption(argLocalPath = new ArgumentBuilder()
                                .withName("local-path")
                                .withDescription("the local path")
                                .withMinimum(0)
                                .withMaximum(1)
                                .create())
                        .create()
                )
                .create();
    }
}