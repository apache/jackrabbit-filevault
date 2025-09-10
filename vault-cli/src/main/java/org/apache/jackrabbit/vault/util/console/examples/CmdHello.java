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
package org.apache.jackrabbit.vault.util.console.examples;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.vault.util.console.ExecutionContext;
import org.apache.jackrabbit.vault.util.console.commands.AbstractCommand;

/**
 * {@code CmdHello}...
 */
public class CmdHello extends AbstractCommand {

    private Option argName;
    private Options options;

    public CmdHello() {
        options = new Options();
        argName = Option.builder()
                .argName("name")
                .desc("print this name. default is 'world'")
                .hasArg()
                .build();
        options.addOption(argName);
    }

    protected void doExecute(ExecutionContext ctx, CommandLine cl)
            throws Exception {
        String name = cl.getOptionValue("name");
        if (name == null) {
            System.out.println("Hello, world.");
        } else {
            System.out.println("Hello, " + name + ".");
        }
    }

    public String getShortDescription() {
        return "print hello";
    }

    public Options getOptions() {
        return options;
    }

}