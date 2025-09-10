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
import org.apache.commons.cli.Options;
import org.apache.commons.cli.HelpFormatter;
import org.apache.jackrabbit.vault.util.console.CliCommand;
import org.apache.jackrabbit.vault.util.console.ExecutionContext;

/**
 * {@code AbstractCommand}...
 */
public abstract class AbstractCommand implements CliCommand {

    private String name;
    private Options options;

    protected AbstractCommand() {
    }

    public boolean execute(ExecutionContext ctx, CommandLine cl) throws Exception {
        doExecute(ctx, cl);
        return true;
    }

    public boolean hasName(String name) {
        return getName().equals(name);
    }

    public String getName() {
        if (name == null) {
            String cls = getClass().getSimpleName();
            if (cls.startsWith("Cmd") && cls.length() > 3) {
                name = cls.substring(3).toLowerCase();
            } else {
                name = cls.toLowerCase();
            }
        }
        return name;
    }

    public String toString() {
        return getName();
    }

    public String getLongDescription() {
        return getShortDescription();
    }

    public String getExample() {
        return null;
    }

    public Options getOptions() {
        if (options == null) {
            options = new Options();
        }
        return options;
    }

    public void printHelp() {
        HelpFormatter hf = new HelpFormatter();
        hf.printHelp(getName(), getOptions());
    }

    protected abstract void doExecute(ExecutionContext ctx, CommandLine cl)
            throws Exception;

}