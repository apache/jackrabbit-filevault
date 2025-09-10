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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.vault.util.console.CliCommand;
import org.apache.commons.cli.HelpFormatter;

/**
 * Implements the 'connect' command.
 *
 */
public class CmdConnect extends AbstractJcrFsCommand {

    private Option optURI;
    private Option optForce;
    private Options options;

    public CmdConnect() {
        options = new Options();
        optForce = Option.builder("f")
                .desc("force reconnect if already connected")
                .build();
        options.addOption(optForce);
        optURI = Option.builder()
                .argName("rmiuri")
                .hasArg()
                .desc("the rmi uri of the repository")
                .build();
        options.addOption(optURI);
        options.addOption(CliCommand.OPT_VERBOSE);
        options.addOption(CliCommand.OPT_QUIET);
    }

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        String uri = cl.getOptionValue(optURI.getOpt());
        if (uri != null) {
            ctx.getVaultFsApp().setProperty(VaultFsApp.KEY_DEFAULT_URI, uri);
        }
        if (ctx.getVaultFsApp().isConnected() && cl.hasOption(optForce.getOpt())) {
            ctx.getVaultFsApp().disconnect();
        }
        ctx.getVaultFsApp().connect();
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Connect to a repository";
    }

    public Options getOptions() { return options; }
    public void printHelp() { new HelpFormatter().printHelp("connect", options); }
}