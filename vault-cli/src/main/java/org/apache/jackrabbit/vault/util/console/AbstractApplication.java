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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;

import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.DisplaySetting;
import org.apache.commons.cli2.Group;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.OptionException;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.commandline.Parser;
import org.apache.commons.cli2.option.Command;
import org.apache.commons.cli2.util.HelpFormatter;
import org.apache.jackrabbit.vault.util.console.util.CliHelpFormatter;
import org.apache.jackrabbit.vault.util.console.util.Log4JConfig;
import org.apache.jackrabbit.vault.util.console.util.PomProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>Console</code>...
 */
public abstract class AbstractApplication {

    /**
     * the default logger
     */
    static final Logger log = LoggerFactory.getLogger(AbstractApplication.class);

    private static final String LOG4J_PROPERTIES = "/org/apache/jackrabbit/vault/util/console/log4j.properties";

    public static final String DEFAULT_CONF_FILENAME = "console.properties";

    public static final String KEY_PROMPT = "prompt";
    public static final String KEY_USER = "user";
    public static final String KEY_PATH = "path";
    public static final String KEY_HOST = "host";
    public static final String KEY_LOGLEVEL = "loglevel";

    /**
     * The global env can be loaded and saved into the console properties.
     */
    private Properties globalEnv = new Properties();

    //private Option optPropertyFile;
    private Option optLogLevel;
    private Option optVersion;
    private Option optHelp;

    public String getVersion() {
        return getPomProperties().getVersion();
    }

    public PomProperties getPomProperties() {
        return new PomProperties("org.apache.jackrabbit.vault", "vault-cli");
    }
    
    public String getCopyrightLine() {
        return "Copyright 2013 by Apache Software Foundation. See LICENSE.txt for more information.";
    }
    
    public String getVersionString() {
        return getApplicationName() + " [version " + getVersion() + "] " + getCopyrightLine();
    }

    public void printVersion() {
        System.out.println(getVersionString());
    }

    /**
     * Returns the name of this application
     *
     * @return the name of this application
     */
    public abstract String getApplicationName();


    /**
     * Returns the name of the shell command
     *
     * @return the name of the shell command
     */
    public abstract String getShellCommand();

    public void printHelp(String cmd) {
        if (cmd == null) {
            getAppHelpFormatter().print();
        } else {
            getDefaultContext().printHelp(cmd);
        }
    }

    protected HelpFormatter getAppHelpFormatter() {
        CliHelpFormatter hf = CliHelpFormatter.create();
        StringBuffer sep = new StringBuffer(hf.getPageWidth());
        while (sep.length() < hf.getPageWidth()) {
            sep.append("-");
        }
        hf.setHeader(getVersionString());
        hf.setDivider(sep.toString());
        hf.setShellCommand("  " + getShellCommand() + " [options] <command> [arg1 [arg2 [arg3] ..]]");
        hf.setGroup(getApplicationCLGroup());
        hf.setSkipToplevel(true);
        hf.getFullUsageSettings().removeAll(DisplaySetting.ALL);

        hf.getDisplaySettings().remove(DisplaySetting.DISPLAY_GROUP_ARGUMENT);
        hf.getDisplaySettings().remove(DisplaySetting.DISPLAY_PARENT_CHILDREN);
        hf.getDisplaySettings().add(DisplaySetting.DISPLAY_OPTIONAL);

        hf.getLineUsageSettings().add(DisplaySetting.DISPLAY_PROPERTY_OPTION);
        hf.getLineUsageSettings().add(DisplaySetting.DISPLAY_PARENT_ARGUMENT);
        hf.getLineUsageSettings().add(DisplaySetting.DISPLAY_ARGUMENT_BRACKETED);
        return hf;
    }

    public Group getApplicationCLGroup() {
        return new GroupBuilder()
                .withName("")
                .withOption(addApplicationOptions(new GroupBuilder()).create())
                .withOption(getDefaultContext().getCommandsGroup())
                .withMinimum(0)
                .create();
    }

    public GroupBuilder addApplicationOptions(GroupBuilder gbuilder) {
        final DefaultOptionBuilder obuilder = new DefaultOptionBuilder();
        final ArgumentBuilder abuilder = new ArgumentBuilder();
        /*
        optPropertyFile =
                obuilder
                        .withShortName("F")
                        .withLongName("console-settings")
                        .withDescription(
                                "The console settings property file. " +
                                        "This is only required for interactive mode.")
                        .withArgument(abuilder
                                .withDescription("defaults to ...")
                                .withMinimum(1)
                                .withMaximum(1)
                                .create()
                        )
                        .create();
        optInteractive =
                obuilder
                        .withShortName("i")
                        .withLongName("interactive")
                        .withDescription("runs this application in an interactive mode.")
                        .create();
        */
        optVersion =
                obuilder
                        .withLongName("version")
                        .withDescription("print the version information and exit")
                        .create();
        optHelp =
                obuilder
                        .withShortName("h")
                        .withLongName("help")
                        .withDescription("print this help")
                        .withArgument(abuilder
                                .withName("command")
                                .withMaximum(1)
                                .create()
                        )
                        .create();

        optLogLevel =
                obuilder
                        .withLongName("log-level")
                        .withDescription("the log4j log level")
                        .withArgument(abuilder
                                .withName("level")
                                .withMaximum(1)
                                .create()
                        )
                        .create();

        gbuilder
                .withName("Global options:")
                //.withOption(optPropertyFile)
                .withOption(CliCommand.OPT_VERBOSE)
                .withOption(CliCommand.OPT_QUIET)
                .withOption(optVersion)
                .withOption(optLogLevel)
                .withOption(optHelp)
                .withMinimum(0);
        /*
        if (getConsole() != null) {
            gbuilder.withOption(optInteractive);
        }
        */
        return gbuilder;
    }

    protected void init() {
        globalEnv.setProperty(KEY_PROMPT,
                "[${" + KEY_USER + "}@${" + KEY_HOST + "} ${" + KEY_PATH  +"}]$ ");
    }

    protected void initLogging() {
        Log4JConfig.init(LOG4J_PROPERTIES);
    }

    protected void run(String[] args) {
        // setup logging
        try {
            initLogging();
        } catch (Throwable e) {
            System.err.println("Error while initializing logging: " + e);
        }

        // setup and start
        init();

        Parser parser = new Parser();
        parser.setGroup(getApplicationCLGroup());
        parser.setHelpOption(optHelp);
        try {
            CommandLine cl = parser.parse(args);
            String logLevel = getEnv().getProperty(KEY_LOGLEVEL);
            if (cl.hasOption(optLogLevel)) {
                logLevel = (String) cl.getValue(optLogLevel);
            }
            if (logLevel != null) {
                Log4JConfig.setLevel(logLevel);
            }
            prepare(cl);
            execute(cl);
        } catch (OptionException e) {
            log.error("{}. Type --help for more information.", e.getMessage());
        } catch (ExecutionException e) {
            log.error("Error while starting: {}", e.getMessage());
        } finally {
            close();
        }
    }

    public void setLogLevel(String level) {
        try {
            Log4JConfig.setLevel(level);
            getEnv().setProperty(KEY_LOGLEVEL, level);
            System.out.println("Log level set to '" + Log4JConfig.getLevel() + "'");
        } catch (Throwable e) {
            System.err.println("Error while setting log level: " + e);
        }
    }

    public void prepare(CommandLine cl) throws ExecutionException {
        /*
        try {
            loadConfig((String) cl.getValue(optPropertyFile));
        } catch (IOException e) {
            throw new ExecutionException("Error while loading property file.", e);
        }
        */
    }

    public void execute(CommandLine cl) throws ExecutionException {
        if (cl.hasOption(optVersion)) {
            printVersion();
        //} else if (cl.hasOption(optInteractive)) {
        //    getConsole().run();
        } else if (cl.hasOption(optHelp)) {
            String cmd = (String) cl.getValue(optHelp);
            if (cmd == null) {
                // in this case, the --help is specified after the command
                // eg: vlt checkout --help
                Iterator iter = cl.getOptions().iterator();
                while (iter.hasNext()) {
                    Object o = iter.next();                    
                    if (o instanceof Command) {
                        cmd = ((Command) o).getPreferredName();
                        break;
                    }
                }
            }
            printHelp(cmd);
        } else {
            if (!getDefaultContext().execute(cl)) {
                log.error("Unknown command. Type '--help' for more information.");
            }
        }
    }

    public void saveConfig(String path) throws IOException {
        File file = new File(path == null ? DEFAULT_CONF_FILENAME : path);
        /*
        Properties props = new Properties();
        Iterator iter = globalEnv.keySet().iterator();
        while (iter.hasNext()) {
            String key = (String) iter.next();
            if (key.startsWith("conf.") || key.startsWith("macro.")) {
                props.put(key, globalEnv.getProperty(key));
            }
        }
        props.store(out, "Console Configuration");
        */
        FileOutputStream out = new FileOutputStream(file);
        globalEnv.store(out, "Console Configuration");
        out.close();
        log.info("Configuration saved to {}", file.getCanonicalPath());
    }

    public void loadConfig(String path) throws IOException {
        File file = new File(path == null ? DEFAULT_CONF_FILENAME : path);
        if (!file.canRead() && path == null) {
            // ignore errors for default config
            return;
        }
        Properties props = new Properties();
        FileInputStream in = new FileInputStream(file);
        props.load(in);
        in.close();
        Iterator iter = globalEnv.keySet().iterator();
        while (iter.hasNext()) {
            String key = (String) iter.next();
            if (!props.containsKey(key)) {
                props.put(key, globalEnv.getProperty(key));
            }
        }
        globalEnv = props;
        log.info("Configuration loaded from {}", file.getCanonicalPath());
    }

    protected void close() {
    }

    public Properties getEnv() {
        return globalEnv;
    }

    public void setProperty(String key, String value) {
        if (value == null) {
            globalEnv.remove(key);
        } else {
            if (key.equals(KEY_LOGLEVEL)) {
                setLogLevel(value);
            } else {
                globalEnv.setProperty(key, value);
            }
        }
    }

    public String getProperty(String key) {
        return globalEnv.getProperty(key);
    }

    protected abstract ExecutionContext getDefaultContext();

    public abstract Console getConsole();
}