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
import org.apache.commons.cli2.option.Command;
import org.apache.jackrabbit.vault.util.console.CliCommand;
import org.apache.jackrabbit.vault.util.console.ExecutionContext;

/**
 * <code>AbstractCommand</code>...
 */
public abstract class AbstractCommand implements CliCommand {

    private Command cmd;

    protected AbstractCommand() {
    }

    public boolean execute(ExecutionContext ctx, CommandLine cl) throws Exception {
        if (cl.hasOption(getCommand())) {
            doExecute(ctx, cl);
            return true;
        } else {
            return false;
        }
    }

    public Option getCommand() {
        if (cmd == null) {
            cmd = createCommand();
        }
        return cmd;
    }

    public boolean hasName(String name) {
        return getCommand().getTriggers().contains(name);
    }

    public String getName() {
        return getCommand().getPreferredName();
    }

    public String toString() {
        return getCommand().toString();
    }

    public String getLongDescription() {
        return getShortDescription();
    }

    public String getExample() {
        return null;
    }

    protected abstract void doExecute(ExecutionContext ctx, CommandLine cl)
            throws Exception;

    protected abstract Command createCommand();
}