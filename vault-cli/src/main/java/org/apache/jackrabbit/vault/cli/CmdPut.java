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

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.fs.api.VaultFsTransaction;
import org.apache.jackrabbit.vault.util.FileInputSource;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.apache.jackrabbit.vault.util.Text;
import org.apache.jackrabbit.vault.util.console.ExecutionException;

/**
 * Implements the 'put' command.
 *
 */
public class CmdPut extends AbstractJcrFsCommand {

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        String name = (String) cl.getValue(argLocalPath);
        String jcrPath = (String) cl.getValue(argJcrPath);

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
    private Argument argLocalPath;
    private Argument argJcrPath;

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("put")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                                /*
                                .withOption(optForce = new DefaultOptionBuilder()
                                        .withShortName("f")
                                        .withDescription("force overwrite if local file already exists")
                                        .create())
                                */
                        .withOption(argLocalPath = new ArgumentBuilder()
                                .withName("local-path")
                                .withDescription("the local path")
                                .withMinimum(1)
                                .withMaximum(1)
                                .create()
                        )
                        .withOption(argJcrPath = new ArgumentBuilder()
                                .withName("jcrl-path")
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