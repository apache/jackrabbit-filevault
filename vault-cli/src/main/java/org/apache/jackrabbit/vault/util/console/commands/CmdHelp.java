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
package org.apache.jackrabbit.vault.util.console.commands;

import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.jackrabbit.vault.util.console.ExecutionContext;

/**
 * <code>CmdExit</code>...
 */
public class CmdHelp extends AbstractCommand {

    private Option argCommand;

    protected void doExecute(ExecutionContext ctx, CommandLine cl) throws Exception {
        String cmd = (String) cl.getValue(argCommand);
        ctx.printHelp(cmd);
    }

    public String getShortDescription() {
        return "print this help.  Type 'help <subcommand>' for help on a specific subcommand.";
    }

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("help")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(argCommand = new ArgumentBuilder()
                                .withName("command")
                                .withDescription("prints the help for the given command")
                                .withMinimum(0)
                                .withMaximum(1)
                                .create()
                        )
                        .create()
                )
                .create();
    }
}