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
package org.apache.jackrabbit.vault.util.console;

import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.DisplaySetting;
import org.apache.commons.cli2.Group;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.commandline.Parser;
import org.apache.commons.cli2.option.Command;
import org.apache.commons.cli2.util.HelpFormatter;

/**
 * <code>Test</code>...
 */
public class Test {

    public static void main(String[] args) throws Exception {
        final DefaultOptionBuilder obuilder = new DefaultOptionBuilder();
        final ArgumentBuilder abuilder = new ArgumentBuilder();
        final CommandBuilder cbuilder = new CommandBuilder();
        final GroupBuilder gbuilder = new GroupBuilder();

        Option recursive =
                obuilder
                        .withShortName("r")
                        .withLongName("recursive")
                        .withDescription("do recursively")
                        .create();
        Command update =
                cbuilder
                        .withName("update")
                        .withName("up")
                        .withDescription("update the work directory")
                        /*
                        .withChildren(new GroupBuilder()
                                .withName("options")
                                .withOption(recursive)
                                .create())
                        .withArgument(abuilder
                                .withName("path")
                                .withMinimum(0)
                                .create()
                        )
                        */
                        .create();
        Option projecthelp =
                obuilder
                        .withShortName("projecthelp")
                        .withShortName("p")
                        .withDescription("print project help information")
                        .create();
        Option version =
                obuilder
                        .withShortName("v")
                        .withDescription("print the version information and exit")
                        .create();

        Group options =
                gbuilder
                        .withName("Global options:")
                        .withOption(projecthelp)
                        .withOption(version)
                        .withMinimum(0)
                        .create();

        Option command = abuilder.withName("command").withMinimum(1).withMaximum(1).create();
        Option targets = abuilder.withName("arg").create();

        CliCommand exit = new org.apache.jackrabbit.vault.util.console.commands.CmdExit();
        CliCommand help = new org.apache.jackrabbit.vault.util.console.commands.CmdHelp();
        CliCommand setenv = new org.apache.jackrabbit.vault.util.console.commands.CmdSet();
        Group commands =
                gbuilder
                        .withName("Commands:")
                        .withOption(help.getCommand())
                        .withOption(exit.getCommand())
                        .withOption(setenv.getCommand())
                        .withOption(update)
                        .withMinimum(0)
                        .withMaximum(1)
                        .create();

        Group main =
                gbuilder
                        .withName("")
                        .withOption(options)
                        .withOption(commands)
                        .withMinimum(0)
                        .create();

        HelpFormatter hf = new HelpFormatter();
        StringBuffer sep = new StringBuffer(hf.getPageWidth());
        while (sep.length() < hf.getPageWidth()) {
            sep.append("-");
        }
        hf.setHeader("File Vault version 1.0");
        hf.setDivider(sep.toString());
        hf.setShellCommand("vlt [options] <command> [arg1 [arg2 [arg3] ..]]");
        hf.setGroup(main);
        //hf.setHeader("bla bla version vla");
        //displayHelp();
        hf.getFullUsageSettings().removeAll(DisplaySetting.ALL);

        //hf.getDisplaySettings().add(DisplaySetting.DISPLAY_PARENT_ARGUMENT);
        //hf.getDisplaySettings().add(DisplaySetting.DISPLAY_PARENT_CHILDREN);

        //hf.getFullUsageSettings().remove(DisplaySetting.DISPLAY_OPTIONAL);
        hf.getFullUsageSettings().remove(DisplaySetting.DISPLAY_GROUP_ARGUMENT);
        hf.getFullUsageSettings().remove(DisplaySetting.DISPLAY_GROUP_EXPANDED);

        hf.getDisplaySettings().remove(DisplaySetting.DISPLAY_GROUP_ARGUMENT);
        hf.getDisplaySettings().remove(DisplaySetting.DISPLAY_PARENT_CHILDREN);
        hf.getDisplaySettings().add(DisplaySetting.DISPLAY_OPTIONAL);

        //hf.getLineUsageSettings().add(DisplaySetting.DISPLAY_PROPERTY_OPTION);
        //hf.getLineUsageSettings().remove(DisplaySetting.DISPLAY_GROUP_ARGUMENT);
        //hf.getLineUsageSettings().add(DisplaySetting.DISPLAY_GROUP_NAME);
        //hf.getLineUsageSettings().remove(DisplaySetting.DISPLAY_PARENT_CHILDREN);
        //hf.getLineUsageSettings().add(DisplaySetting.DISPLAY_GROUP_ARGUMENT);
        //hf.getLineUsageSettings().remove(DisplaySetting.DISPLAY_GROUP_EXPANDED);
        //hf.getLineUsageSettings().remove(DisplaySetting.DISPLAY_PARENT_CHILDREN);
        //hf.getLineUsageSettings().remove(DisplaySetting.DISPLAY_PARENT_CHILDREN);

        hf.getLineUsageSettings().add(DisplaySetting.DISPLAY_PROPERTY_OPTION);
        hf.getLineUsageSettings().add(DisplaySetting.DISPLAY_PARENT_ARGUMENT);
        hf.getLineUsageSettings().add(DisplaySetting.DISPLAY_ARGUMENT_BRACKETED);

        Parser parser = new Parser();
        parser.setHelpFormatter(hf);
        parser.setGroup(main);
        System.out.println(main);

        CommandLine cl = parser.parseAndHelp(args);
        if (cl != null) {
            if (exit.execute(null, cl)) {
                System.out.println("exit executed");
            } else if (help.execute(null, cl)) {
                System.out.println("help executed.");
            } else {
                hf.print();
            }
        }
    }
}