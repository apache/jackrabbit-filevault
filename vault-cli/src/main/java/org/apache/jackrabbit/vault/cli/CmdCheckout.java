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
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.util.console.CliCommand;
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
    private Options options;

    public CmdCheckout() {
        options = new Options();
        optForce = Option.builder()
                .longOpt("force")
                .desc("force checkout to overwrite local files if they already exist.")
                .build();
        options.addOption(optForce);
        options.addOption(CliCommand.OPT_VERBOSE);
        options.addOption(CliCommand.OPT_QUIET);
    }

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl)
            throws Exception {
        // overwrite this, since it takes the mounted vault fs into account
        String[] args = cl.getArgs();
        String jcrPath = args != null && args.length > 0 ? args[0] : null;
        String localPath = args != null && args.length > 1 ? args[1] : null;

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
        vCtx.setVerbose(cl.hasOption(OPT_VERBOSE.getOpt()));
        vCtx.setQuiet(cl.hasOption(OPT_QUIET.getOpt()));
        Checkout c = new Checkout(addr, jcrPath, localFile);
        c.setForce(cl.hasOption(optForce.getOpt()));
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

    public Options getOptions() { return options; }
    public void printHelp() { new HelpFormatter().printHelp("checkout|co [options] <jcrPath> [localPath]", options); }
}