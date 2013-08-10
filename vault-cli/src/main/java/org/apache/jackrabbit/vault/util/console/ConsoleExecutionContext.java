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

import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.cli2.CommandLine;
import org.apache.jackrabbit.vault.util.console.commands.CmdEnv;
import org.apache.jackrabbit.vault.util.console.commands.CmdExec;
import org.apache.jackrabbit.vault.util.console.commands.CmdExit;
import org.apache.jackrabbit.vault.util.console.commands.CmdHelp;
import org.apache.jackrabbit.vault.util.console.commands.CmdHistory;
import org.apache.jackrabbit.vault.util.console.commands.CmdLoad;
import org.apache.jackrabbit.vault.util.console.commands.CmdPwd;
import org.apache.jackrabbit.vault.util.console.commands.CmdSet;
import org.apache.jackrabbit.vault.util.console.commands.CmdStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>Console</code>...
 */
public class ConsoleExecutionContext extends ExecutionContext {

    /**
     * the default logger
     */
    static final Logger log = LoggerFactory.getLogger(ConsoleExecutionContext.class);

    private final String name;

    /**
     * holds 'runtime' properties.
     */
    private Properties runtimeEnv = new Properties();

    private Console console;

    private ConsoleFile rootFile;

    private ConsoleFile currentFile;

    public ConsoleExecutionContext(AbstractApplication app) {
        this(app, "default");
    }

    public ConsoleExecutionContext(AbstractApplication app, String name) {
        super(app);
        this.name = name;

        // init commands
        installCommand(new CmdHelp());
        installCommand(new CmdEnv());
        installCommand(new CmdHistory());
        installCommand(new CmdSet());
        installCommand(new CmdPwd());
        installCommand(new CmdExit());
        installCommand(new CmdStore());
        installCommand(new CmdLoad());
        installCommand(new CmdExec());
    }

    public void attach(Console console) {
        this.console = console;
    }

    public Console getConsole() {
        return console;
    }

    public String getName() {
        return name;
    }

    public void setFileSystem(ConsoleFile rootFile) {
        this.rootFile = rootFile;
        setCurrentFile(rootFile);
    }

    public ConsoleFile getRootFile() {
        return rootFile;
    }

    public ConsoleFile getCurrentFile() {
        assertFs();
        return currentFile;
    }

    public ConsoleFile getFile(String path, boolean mustExist) {
        assertFs();
        if (path == null || path.equals(".")) {
            return currentFile;
        }
        try {
            return currentFile.getFile(path, mustExist);
        } catch (IOException e) {
            throw new ExecutionException(e);
        }
    }

    public void cd(String path) {
        setCurrentFile(getFile(path, true));
    }

    public void setCurrentFile(ConsoleFile currentFile) {
        assertFs();
        this.currentFile = currentFile;
        runtimeEnv.setProperty(AbstractApplication.KEY_PATH, currentFile.getPath());
    }

    private void assertFs() {
        if (rootFile == null) {
            throw new ExecutionException("This context holds no fs.");
        }
    }

    protected boolean doExecute(CliCommand cmd, CommandLine cl) throws Exception {
        if (cmd instanceof ConsoleCommand) {
            return (((ConsoleCommand) cmd).execute(this, cl));
        } else {
            return super.doExecute(cmd, cl);
        }
    }

    public Properties getRuntimeEnv() {
        return runtimeEnv;
    }

    public Set getPropertyKeys() {
        Set ret = new HashSet();
        ret.addAll(getApplication().getEnv().keySet());
        ret.addAll(runtimeEnv.keySet());
        return ret;
    }
    
    public String getProperty(String key) {
        return runtimeEnv.getProperty(key, getApplication().getProperty(key));
    }


}