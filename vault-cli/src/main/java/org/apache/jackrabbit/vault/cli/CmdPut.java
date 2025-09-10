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
import java.io.IOException;

import javax.jcr.RepositoryException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.util.FileInputSource;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.util.console.ExecutionException;

/**
 * Implements the 'put' command.
 *
 */
public class CmdPut extends AbstractJcrFsCommand {

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        String name = cl.getOptionValue("local-path");
        String jcrPath = cl.getOptionValue("jcr-path");

        VaultFile file;
        if (jcrPath == null) {
            jcrPath = Text.getName(name);
            if (jcrPath.equals("")) {
                jcrPath = name;
            }
        }
        File local = ctx.getVaultFsApp().getPlatformFile(name, true);
        VaultFile cwd = ctx.getVaultFsApp().getVaultFile("", true);
        file = cwd.getFileSystem().getFile(cwd, jcrPath);
        if (file == null) {
            // do add
            jcrPath = PathUtil.makePath(cwd.getPath(), jcrPath);
            doAdd(local, cwd, jcrPath);
        } else {
            doPut(local, file);
        }
    }

    private void doPut(File local, VaultFile remote) {
        try {
            FileInputSource is = new FileInputSource(local);
            VaultFsTransaction tx = remote.getFileSystem().startTransaction();
            tx.modify(remote, is);
            tx.commit();
            System.out.println(local.getName() + "  " + local.length() + " bytes.");
        } catch (IOException e) {
            throw new ExecutionException("Error while uploading file: " + e);
        } catch (RepositoryException e) {
            throw new ExecutionException("Error while uploading file: " + e);
        }
    }

    private void doAdd(File local, VaultFile parent, String path) {
        try {
            FileInputSource is = new FileInputSource(local);
            VaultFsTransaction tx = parent.getFileSystem().startTransaction();
            tx.add(path, is);
            tx.commit();
            System.out.println(local.getName() + "  " + local.length() + " bytes.");
        } catch (IOException e) {
            throw new ExecutionException("Error while uploading file: " + e);
        } catch (RepositoryException e) {
            throw new ExecutionException("Error while uploading file: " + e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Upload a file";
    }


    public String getLongDescription() {
        return "Uploads a Vault file from the local file system to the remote " +
                "Vault filesystem. If no remote path is given it uses the " +
                "the same name as the local file.";
    }

    //private Option optForce;
    private Option argLocalPath;
    private Option argJcrPath;
    private Options options;

    public CmdPut() {
        options = new Options();
        argLocalPath = Option.builder()
                .argName("local-path")
                .desc("the local path")
                .hasArg()
                .build();
        options.addOption(argLocalPath);
        argJcrPath = Option.builder()
                .argName("jcr-path")
                .desc("the jcr path")
                .hasArg()
                .build();
        options.addOption(argJcrPath);
    }

    public Options getOptions() {
        return options;
    }
}