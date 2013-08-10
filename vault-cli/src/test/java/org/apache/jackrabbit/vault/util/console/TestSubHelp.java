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

import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.jackrabbit.vault.util.console.commands.CmdSet;
import org.apache.jackrabbit.vault.util.console.util.CliHelpFormatter;

/**
 * <code>Test</code>...
 */
public class TestSubHelp {

    public static void main(String[] args) throws Exception {
        final DefaultOptionBuilder obuilder = new DefaultOptionBuilder();
        final ArgumentBuilder abuilder = new ArgumentBuilder();
        final CommandBuilder cbuilder = new CommandBuilder();
        final GroupBuilder gbuilder = new GroupBuilder();

        CmdSet set = new CmdSet();

        CliHelpFormatter hf = CliHelpFormatter.create();
        hf.setCmd(set);
        //hf.setHeader("bla bla version vla");
        //displayHelp();
        //hf.getFullUsageSettings().remove(DisplaySetting.DISPLAY_OPTIONAL);

        //hf.getDisplaySettings().add(DisplaySetting.DISPLAY_PARENT_ARGUMENT);
        //hf.getDisplaySettings().add(DisplaySetting.DISPLAY_PARENT_CHILDREN);

        //hf.getFullUsageSettings().remove(DisplaySetting.DISPLAY_OPTIONAL);
        //hf.getFullUsageSettings().remove(DisplaySetting.DISPLAY_GROUP_ARGUMENT);
        //hf.getFullUsageSettings().remove(DisplaySetting.DISPLAY_GROUP_EXPANDED);

        //hf.getDisplaySettings().remove(DisplaySetting.DISPLAY_GROUP_ARGUMENT);
        //hf.getDisplaySettings().remove(DisplaySetting.DISPLAY_PARENT_CHILDREN);
        //hf.getDisplaySettings().add(DisplaySetting.DISPLAY_OPTIONAL);

        //hf.getLineUsageSettings().add(DisplaySetting.DISPLAY_PROPERTY_OPTION);
        //hf.getLineUsageSettings().remove(DisplaySetting.DISPLAY_GROUP_ARGUMENT);
        //hf.getLineUsageSettings().add(DisplaySetting.DISPLAY_GROUP_NAME);
        //hf.getLineUsageSettings().remove(DisplaySetting.DISPLAY_PARENT_CHILDREN);
        //hf.getLineUsageSettings().add(DisplaySetting.DISPLAY_GROUP_ARGUMENT);
        //hf.getLineUsageSettings().remove(DisplaySetting.DISPLAY_GROUP_EXPANDED);
        //hf.getLineUsageSettings().remove(DisplaySetting.DISPLAY_PARENT_CHILDREN);
        //hf.getLineUsageSettings().remove(DisplaySetting.DISPLAY_PARENT_CHILDREN);

        //hf.getLineUsageSettings().add(DisplaySetting.DISPLAY_PROPERTY_OPTION);
        //hf.getLineUsageSettings().add(DisplaySetting.DISPLAY_PARENT_ARGUMENT);
        //hf.getLineUsageSettings().add(DisplaySetting.DISPLAY_ARGUMENT_BRACKETED);

        hf.print();
    }
}