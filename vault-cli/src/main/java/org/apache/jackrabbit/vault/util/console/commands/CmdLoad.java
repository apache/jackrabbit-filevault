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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.vault.util.console.AbstractApplication;
import org.apache.jackrabbit.vault.util.console.ConsoleExecutionContext;

/**
 * {@code CmdLs}...
 */
public class CmdLoad extends AbstractConsoleCommand {

    private Option argFile;
    private Options options;

    public CmdLoad() {
        options = new Options();
        argFile = Option.builder()
                .argName("file")
                .hasArg()
                .desc("specifies the config file. default is \"" + AbstractApplication.DEFAULT_CONF_FILENAME + "\"")
                .build();
        options.addOption(argFile);
    }

    protected void doExecute(ConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        String file = cl.getOptionValue(argFile.getOpt());
        ctx.getApplication().loadConfig(file);
    }

    public String getShortDescription() {
        return "load a console configuration";
    }

    public Options getOptions() { return options; }
    public void printHelp() { new HelpFormatter().printHelp("load", options); }
}