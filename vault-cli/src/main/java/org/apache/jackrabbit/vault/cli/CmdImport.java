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

import javax.jcr.Node;
import javax.jcr.Session;

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.FileArchive;
import org.apache.jackrabbit.vault.fs.io.Importer;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.util.DefaultProgressListener;

/**
 * Implements the 'import' command.
 *
 */
public class CmdImport extends AbstractJcrFsCommand {

    //private Option optExclude;
    private Argument argLocalPath;
    private Argument argJcrPath;

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl) throws Exception {
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
        File localFile = ctx.getVaultFsApp().getPlatformFile(localPath, false);
        VaultFile vaultFile = ctx.getVaultFsApp().getVaultFile(jcrPath, true);
        VaultFsApp.log.info("Importing {} to {}", localFile.getCanonicalPath(), vaultFile.getPath());
        Archive archive;
        if (localFile.isFile()) {
            archive = new ZipArchive(localFile);
        } else {
            archive = new FileArchive(localFile);
        }
        Importer importer = new Importer();
        if (verbose) {
            importer.getOptions().setListener(new DefaultProgressListener());
        }

        Session s = vaultFile.getFileSystem().getAggregateManager().getSession();
        Node importRoot = s.getNode(vaultFile.getPath());
        importer.run(archive, importRoot);
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
                "Vault filesystem at <jcrl-path>. Both paths can be relative " +
                "to their respective CWDs.";
    }

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("import")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(OPT_VERBOSE)
                        /*
                        .withOption(optExclude = new DefaultOptionBuilder()
                                .withShortName("e")
                                .withDescription("specifies the excluded local files. can be multiple.")
                                .withArgument(new ArgumentBuilder()
                                        .withMinimum(0)
                                        .create())
                                .create())
                        */
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