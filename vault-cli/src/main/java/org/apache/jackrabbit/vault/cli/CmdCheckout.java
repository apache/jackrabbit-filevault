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
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.util.Text;
import org.apache.jackrabbit.vault.util.console.ExecutionException;
import org.apache.jackrabbit.vault.vlt.VltContext;
import org.apache.jackrabbit.vault.vlt.actions.Checkout;

/**
 * Implements the 'checkout' command. there are 2 forms, either:
 * co rmi://localhost/crx.default/apps/components
 *
 * or:
 * co --mountpoint=rmi://localhost/crx.default /apps/components
 *
 *
 */
public class CmdCheckout extends AbstractJcrFsCommand {

    private Option optForce;
    //private Option optExclude;
    private Argument argLocalPath;
    private Argument argJcrPath;

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl)
            throws Exception {
        // overwrite this, since it takes the mounted vault fs into account
        String jcrPath = (String) cl.getValue(argJcrPath);
        String localPath = (String) cl.getValue(argLocalPath);

        VaultFile remoteFile = ctx.getVaultFsApp().getVaultFile(jcrPath, true);
        RepositoryAddress addr = remoteFile.getAggregate().getManager().getMountpoint();

        if (localPath == null) {
            if (jcrPath == null) {
                localPath = Text.getName(addr.toString());
            } else {
                localPath = Text.getName(jcrPath);
            }
        }
        File localFile = ctx.getVaultFsApp().getPlatformFile(localPath, false);
        if (localFile.isFile()) {
            throw new ExecutionException("Local file must be a directory: " + localFile.getPath());
        }
        if (!localFile.exists()) {
            localFile.mkdir();
        }

        VltContext vCtx = ctx.getVaultFsApp().createVaultContext(localFile);
        vCtx.setVerbose(cl.hasOption(OPT_VERBOSE));
        vCtx.setQuiet(cl.hasOption(OPT_QUIET));
        Checkout c = new Checkout(addr, jcrPath, localFile);
        c.setForce(cl.hasOption(optForce));
        vCtx.execute(c);
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Checkout a Vault file system";
    }

    public String getLongDescription() {
        return  "Checkout the Vault file system (starting at <jcrPath> to the " +
                "local filesystem at <local-path>.";
    }

    protected Command createCommand() {
        argJcrPath = new ArgumentBuilder()
            .withName("jcrPath")
            .withDescription("remote path")
            .withMinimum(1)
            .withMaximum(1)
            .create();
        argLocalPath = new ArgumentBuilder()
            .withName("localPath")
            .withDescription("local path (optional)")
            .withMinimum(0)
            .withMaximum(1)
            .create();
        return new CommandBuilder()
                .withName("checkout")
                .withName("co")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(optForce = new DefaultOptionBuilder()
                                .withLongName("force")
                                .withDescription("force checkout to overwrite local files if they already exist.")
                                .create())
                        .withOption(OPT_VERBOSE)
                        .withOption(OPT_QUIET)
                        .withOption(argJcrPath)
                        .withOption(argLocalPath)
                        .create()
                )
                .create();
    }
}