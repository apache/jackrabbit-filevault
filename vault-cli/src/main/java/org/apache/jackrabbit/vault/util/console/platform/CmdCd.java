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
package org.apache.jackrabbit.vault.util.console.platform;

import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.jackrabbit.vault.util.console.ConsoleExecutionContext;
import org.apache.jackrabbit.vault.util.console.commands.AbstractConsoleCommand;

/**
 * <code>CmdCd</code>...
 */
public class CmdCd extends AbstractConsoleCommand {

    private Option argPath;

    protected void doExecute(ConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        String path = (String) cl.getValue(argPath, "/");
        ctx.cd(path);
    }

    public String getShortDescription() {
        return "change the current work directory";
    }

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("cd")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(argPath = new ArgumentBuilder()
                                        .withName("path")
                                        .withDescription("destination path. changes to root directory if missing.")
                                        .withMinimum(0)
                                        .withMaximum(1)
                                        .create())
                        .create())
                .create();
    }

}