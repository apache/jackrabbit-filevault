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

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;

/**
 * Implements the 'connect' command.
 *
 */
public class CmdConnect extends AbstractJcrFsCommand {

    private Argument argURI;

    private Option optForce;

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        String uri = (String) cl.getValue(argURI);
        if (uri != null) {
            ctx.getVaultFsApp().setProperty(VaultFsApp.KEY_DEFAULT_URI, uri);
        }
        if (ctx.getVaultFsApp().isConnected() && cl.hasOption(optForce)) {
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

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("connect")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(optForce = new DefaultOptionBuilder()
                                .withShortName("f")
                                .withDescription("force reconnect if already connected")
                                .create())
                        .withOption(argURI = new ArgumentBuilder()
                                .withName("rmiuri")
                                .withDescription("the rmi uri of the repository")
                                .withMinimum(0)
                                .withMaximum(1)
                                .create()
                        )
                        .create()
                )
                .create();
    }

}