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

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.commons.cli2.validation.FileValidator;
import org.apache.jackrabbit.vault.util.console.AbstractApplication;
import org.apache.jackrabbit.vault.util.console.ExecutionContext;

/**
 * <code>CmdShell</code>...
 */
public class CmdConsole extends AbstractCommand {

    private Argument argFile;

    protected void doExecute(ExecutionContext ctx, CommandLine cl) throws Exception {
        String file = (String) cl.getValue(argFile);
        ctx.getApplication().loadConfig(file);
        ctx.getApplication().getConsole().run();
    }

    public String getShortDescription() {
        return "Run an interactive console";
    }

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("console")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(new DefaultOptionBuilder()
                                .withShortName("F")
                                .withLongName("console-settings")
                                .withDescription("specifies the console settings file. default is \"" + AbstractApplication.DEFAULT_CONF_FILENAME + "\"")
                                .withArgument(argFile = new ArgumentBuilder()
                                .withName("file")
                                .withMinimum(1)
                                .withMaximum(1)
                                .withValidator(FileValidator.getExistingFileInstance())
                                .create())
                                .create()
                                )
                        .create())
                .create();
    }
}