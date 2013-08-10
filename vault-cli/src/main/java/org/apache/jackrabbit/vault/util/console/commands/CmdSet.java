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

import java.util.Iterator;

import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.jackrabbit.vault.util.console.ConsoleExecutionContext;
import org.apache.jackrabbit.vault.util.console.util.Table;

/**
 * <code>CmdSetenv</code>...
 */
public class CmdSet extends AbstractConsoleCommand {

    private Option argKey;

    private Option argValue;

    protected void doExecute(ConsoleExecutionContext ctx, CommandLine cl)
            throws Exception {
        String key = (String) cl.getValue(argKey);
        String value = (String) cl.getValue(argValue);
        if (key == null) {
            Table t = new Table(2);
            Iterator iter = ctx.getPropertyKeys().iterator();
            while (iter.hasNext()) {
                key = (String) iter.next();
                t.addRow(key, ctx.getProperty(key));
            }
            t.sort(0);
            t.print();
        } else {
            ctx.getApplication().setProperty(key, value);
        }
    }

    public String getShortDescription() {
        return "set a property or displays the environment";
    }

    public String getLongDescription() {
        return  "Sets an environment property. If no argument is given the " +
                "current environment is displayed.\n" +
                "Please note that some properties are read-only and cannot be" +
                "set.";
    }

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("set")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(argKey= new ArgumentBuilder()
                                .withName("key")
                                .withDescription("name of the property")
                                .withMinimum(0)
                                .withMaximum(1)
                                .create()
                        )
                        .withOption(argValue= new ArgumentBuilder()
                                .withName("value")
                                .withDescription("value of the property. " +
                                        "If empty the property will be deleted")
                                .withMinimum(0)
                                .withMaximum(1)
                                .create()
                        )
                        .create()
                )
                .create();
    }

}