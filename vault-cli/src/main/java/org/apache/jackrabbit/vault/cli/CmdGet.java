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
import java.io.FileOutputStream;
import java.io.IOException;

import javax.jcr.RepositoryException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;
import org.apache.jackrabbit.vault.util.console.ExecutionException;

/**
 * Implements the 'get' command.
 *
 */
public class CmdGet extends AbstractJcrFsCommand {

    private Option optForce;
    private Option optJcrPath;
    private Option optLocalPath;
    private Options options;

    public CmdGet() {
        options = new Options();
        optForce = Option.builder("f")
                .desc("force overwrite if local file already exists")
                .build();
        options.addOption(optForce);
        optJcrPath = Option.builder()
                .argName("jcr-path")
                .hasArg()
                .required()
                .desc("the jcr path")
                .build();
        options.addOption(optJcrPath);
        optLocalPath = Option.builder()
                .argName("local-path")
                .hasArg()
                .desc("the local path")
                .build();
        options.addOption(optLocalPath);
    }

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        boolean forced = cl.hasOption(optForce.getOpt());
        String jcrPath = cl.getOptionValue(optJcrPath.getOpt());
        String name = cl.getOptionValue(optLocalPath.getOpt());

        ConsoleFile wo = ctx.getFile(jcrPath, true);
        if (wo instanceof VaultFsCFile) {
            VaultFile file = (VaultFile) wo.unwrap();
            if (name == null) {
                name = file.getName();
            }
            File local = ctx.getVaultFsApp().getPlatformFile(name, false);
            doGet(file, local, forced);
        } else {
            throw new ExecutionException("'get' only possible in jcr fs context");
        }
    }

    private void doGet(VaultFile remote, File local, boolean forced) {
        if (local.exists() && !forced) {
            throw new ExecutionException("Local file already exists. Use -f to overwrite: " + local.getName());
        }
        try {
            FileOutputStream out = new FileOutputStream(local);
            remote.getArtifact().spool(out);
            System.out.println(local.getName() + "  " + local.length() + " bytes.");
            long lastMod = remote.lastModified();
            if (lastMod > 0) {
                local.setLastModified(lastMod);
            }
        } catch (IOException e) {
            throw new ExecutionException("Error while downloading file: " + e);
        } catch (RepositoryException e) {
            throw new ExecutionException("Error while downloading file: " + e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Retrieve a file";
    }


    public String getLongDescription() {
        return  "Retrieve a Jcr file from the repository and stores in to " +
                "the local filesystem. If no local name is given it uses the " +
                "the same name as the remote file.";

    }

    public Options getOptions() { return options; }

    public void printHelp() { new HelpFormatter().printHelp("get", options); }

}