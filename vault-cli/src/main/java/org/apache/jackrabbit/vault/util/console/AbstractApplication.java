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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.util.Iterator;
import java.util.Properties;
import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.jackrabbit.vault.util.console.util.PomProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

/**
 * {@code Console}...
 */
public abstract class AbstractApplication {

    /**
     * the default logger
     */
    static final Logger log = LoggerFactory.getLogger(AbstractApplication.class);

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
    private Options applicationOptions;

    public String getVersion() {
        return getPomProperties().getVersion();
    }

    public PomProperties getPomProperties() {
        return new PomProperties("org.apache.jackrabbit.vault", "vault-cli");
    }
    
    public String getCopyrightLine() {
        return "copyright 2013 by Apache Software Foundation. See LICENSE.txt for more information.";
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
        return new HelpFormatter();
    }

    public Options getApplicationOptions() {
        if (applicationOptions == null) {
            applicationOptions = new Options();
            optVersion = Option.builder()
                    .longOpt("version")
                    .desc("print the version information and exit")
                    .build();
            optHelp = Option.builder("h")
                    .longOpt("help")
                    .hasArg()
                    .argName("command")
                    .desc("print this help")
                    .build();
            optLogLevel = Option.builder()
                    .longOpt("log-level")
                    .hasArg()
                    .argName("level")
                    .desc("the logback log level")
                    .build();

            applicationOptions.addOption(CliCommand.OPT_VERBOSE);
            applicationOptions.addOption(CliCommand.OPT_QUIET);
            applicationOptions.addOption(optVersion);
            applicationOptions.addOption(optLogLevel);
            applicationOptions.addOption(optHelp);
        }
        return applicationOptions;
    }

    protected void init() {
        globalEnv.setProperty(KEY_PROMPT,
                "[${" + KEY_USER + "}@${" + KEY_HOST + "} ${" + KEY_PATH  +"}]$ ");
    }

    protected void run(String[] args) {
        // setup and start
        init();
        DefaultParser parser = new DefaultParser();
        try {
            CommandLine cl = parser.parse(getApplicationOptions(), args, true);
            String logLevel = getEnv().getProperty(KEY_LOGLEVEL);
            if (cl.hasOption(optLogLevel.getOpt())) {
                logLevel = cl.getOptionValue(optLogLevel.getOpt());
            }
            if (logLevel != null) {
                LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
                ch.qos.logback.classic.Logger rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
                rootLogger.setLevel(Level.toLevel(logLevel));
            }
            if (cl.hasOption(optVersion.getOpt())) {
                printVersion();
                return;
            }
            if (cl.hasOption(optHelp.getOpt())) {
                String cmd = cl.getOptionValue(optHelp.getOpt());
                printHelp(cmd);
                return;
            }
            prepare(cl);
            String[] remaining = cl.getArgs();
            if (!getDefaultContext().execute(remaining)) {
                // nothing handled
            }
        } catch (ParseException e) {
            log.error("{}. Type --help for more information.", e.getMessage());
        } catch (ExecutionException e) {
            log.error("Error while starting: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error while starting: {}", e.getMessage(), e);
        } finally {
            close();
        }
    }

    public void setLogLevel(String level) {
        try {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            // overwrite level of configuration file
            ch.qos.logback.classic.Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
            Level logLevel = Level.toLevel(level);
            rootLogger.setLevel(Level.toLevel(level));
            getEnv().setProperty(KEY_LOGLEVEL, level);
            System.out.println("Log level set to '" + logLevel + "'");
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
        try (FileOutputStream out = new FileOutputStream(file)) {
            globalEnv.store(out, "Console Configuration");
        }
        log.info("Configuration saved to {}", file.getCanonicalPath());
    }

    public void loadConfig(String path) throws IOException {
        File file = new File(path == null ? DEFAULT_CONF_FILENAME : path);
        if (!file.canRead() && path == null) {
            // ignore errors for default config
            return;
        }
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        }
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
