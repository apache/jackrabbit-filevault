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

import java.io.File;
import java.io.IOException;

import org.apache.jackrabbit.vault.util.console.AbstractApplication;
import org.apache.jackrabbit.vault.util.console.Console;
import org.apache.jackrabbit.vault.util.console.ConsoleExecutionContext;
import org.apache.jackrabbit.vault.util.console.ExecutionContext;
import org.apache.jackrabbit.vault.util.console.ExecutionException;
import org.apache.jackrabbit.vault.util.console.commands.CmdConsole;
import org.apache.jackrabbit.vault.util.console.commands.CmdCtx;
import org.apache.jackrabbit.vault.util.console.examples.CmdHello;

/**
 * <code>HelloWorldApp</code>...
 */
public class ShellApp extends AbstractApplication {

    private ExecutionContext ctx;

    private ConsoleExecutionContext iCtx;

    private Console console;

    public ShellApp() {
    }

    public String getSVNVersion() {
        return "$Revision: 29457 $";
    }

    public String getApplicationName() {
        return "Shell Console Example";
    }

    public String getShellCommand() {
        return "jash";
    }

    protected ExecutionContext getDefaultContext() {
        if (ctx == null) {
            ctx = new ExecutionContext(this);
            ctx.installCommand(new CmdHello());
            ctx.installCommand(new CmdConsole());
        }
        return ctx;
    }

    public Console getConsole() {
        if (console == null) {
            console = new Console(this);
            iCtx = new ConsoleExecutionContext(this);
            iCtx.installCommand(new CmdLs());
            iCtx.installCommand(new CmdCd());
            iCtx.installCommand(new CmdCtx());
            try {
                iCtx.setFileSystem(new PlatformFile(new File(".").getCanonicalFile()));
            } catch (IOException e) {
                throw new ExecutionException(e);
            }
            console.addContext(iCtx);
        }
        return console;
    }

    public static void main(String[] args) {
        new ShellApp().run(args);
    }
}