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

package org.apache.jackrabbit.vault.cli;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.jcr.Credentials;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.jackrabbit.spi2dav.ConnectionOptions;
import org.apache.jackrabbit.vault.cli.extended.ExtendedOption;
import org.apache.jackrabbit.vault.cli.extended.XDavEx;
import org.apache.jackrabbit.vault.cli.extended.XJcrLog;
import org.apache.jackrabbit.vault.fs.Mounter;
import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.fs.api.VaultFileSystem;
import org.apache.jackrabbit.vault.fs.api.VaultFsConfig;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.AbstractVaultFsConfig;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ExportRoot;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.util.RepositoryProvider;
import org.apache.jackrabbit.vault.util.console.AbstractApplication;
import org.apache.jackrabbit.vault.util.console.Console;
import org.apache.jackrabbit.vault.util.console.ExecutionContext;
import org.apache.jackrabbit.vault.util.console.ExecutionException;
import org.apache.jackrabbit.vault.util.console.commands.CmdConsole;
import org.apache.jackrabbit.vault.util.console.util.CliHelpFormatter;
import org.apache.jackrabbit.vault.util.console.util.Log4JConfig;
import org.apache.jackrabbit.vault.util.console.util.PomProperties;
import org.apache.jackrabbit.vault.vlt.ConfigCredentialsStore;
import org.apache.jackrabbit.vault.vlt.CredentialsStore;
import org.apache.jackrabbit.vault.vlt.VltContext;
import org.apache.jackrabbit.vault.vlt.VltDirectory;
import org.apache.jackrabbit.vault.vlt.meta.MetaDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a console/shell that operates on a jcrfs.
 *
 */
public class VaultFsApp extends AbstractApplication {

    private static final String LOG4J_PROPERTIES = "/org/apache/jackrabbit/vault/cli/log4j.properties";

    public static final String KEY_URI = "uri";
    public static final String KEY_WORKSPACE = "workspace";
    public static final String KEY_MOUNTPOINT = "mountpoint";

    public static final String KEY_DEFAULT_WORKSPACE = "conf.workspace";
    public static final String KEY_DEFAULT_MOUNTPOINT = "conf.mountpoint";
    public static final String KEY_DEFAULT_CREDS = "conf.credentials";
    public static final String KEY_DEFAULT_URI = "conf.uri";
    public static final String KEY_DEFAULT_CONFIG_XML = "conf.configxml";
    public static final String KEY_DEFAULT_FILTER_XML = "conf.filterxml";

    private static final String DEFAULT_URI = "http://localhost:8080/crx/server/crx.default";
    private static final String DEFAULT_WSP = "crx.default";
    private static final String DEFAULT_CREDS = "admin:admin";

    protected static Logger log = LoggerFactory.getLogger(VaultFsApp.class);

    private RepositoryProvider repProvider;

    private CredentialsStore credentialsStore;
    private ConfigCredentialsStore confCredsProvider;

    private Repository rep;

    private Session session;

    private VaultFileSystem fs;

    //private Option optURI;
    //private Option optWorkspace;
    private Option optCreds;
    //private Option optMountpoint;
    private Option optConfig;
    private Option optUpdateCreds;
    // connection options
    private Option optAllowSelfSignedCertificate;
    private Option optDisableHostnameValidation;
    private Option optConnectionTimeoutMs;
    private Option optRequestTimeoutMs;
    private Option optSocketTimeoutMs;
    private Option optUseSystemProperties;
    private Option optProxyHost;
    private Option optProxyPort;
    private Option optProxyProtocol;
    private Option optProxyUsername;
    private Option optProxyPassword;

    private ExtendedOption[] xOpts = new ExtendedOption[]{
            new XJcrLog(),
            new XDavEx()
    };

    private ExecutionContext ctxDefault;

    private VaultFsConsoleExecutionContext ctxPlatform;
    private VaultFsConsoleExecutionContext ctxRepository;
    private VaultFsConsoleExecutionContext ctxJcrfs;
    private VaultFsConsoleExecutionContext ctxAfct;

    private Console console;


    public static void main(String[] args) {
        new VaultFsApp().run(args);
    }

    public VaultFsApp() {
    }

    public VltContext createVaultContext(File localFile) {
        try {
            // hack for setting the default credentials
            if (ctxRepository != null) {
                confCredsProvider.setDefaultCredentials(getProperty(KEY_DEFAULT_CREDS));
            }
            File cwd = getPlatformFile("", true).getCanonicalFile();
            return new VltContext(cwd, localFile, repProvider, credentialsStore);
        } catch (IOException e) {
            throw new ExecutionException(e);
        } catch (ConfigurationException e) {
            throw new ExecutionException(e);
        }
    }

    protected void login(String creds, String wsp) {
        if (rep == null) {
            throw new ExecutionException("Not connected to repository.");
        }
        if (session != null) {
            log.info("Already logged in to repository");
            return;
        }

        if (creds == null) {
            creds = getProperty(KEY_DEFAULT_CREDS);
        }
        if (creds == null) {
            creds = DEFAULT_CREDS;
        }
        if (wsp == null) {
            wsp = getProperty(KEY_DEFAULT_WORKSPACE);
        }
        if (wsp == null) {
            wsp = DEFAULT_WSP;
        }
        Credentials defaultCreds;
        int idx = creds.indexOf(':');
        if (idx > 0) {
            defaultCreds = new SimpleCredentials(creds.substring(0, idx), creds.substring(idx + 1).toCharArray());
        } else {
            defaultCreds = new SimpleCredentials(creds, new char[0]);
        }
        try {
            session = rep.login(defaultCreds, wsp);
        } catch (RepositoryException e) {
            throw new ExecutionException("Failed to login to repository.", e);
        }

        try {
            // install repository contexts
            ctxRepository = new RepExecutionContext(this, "rep", session.getRootNode());
            console.addContext(ctxRepository);
        } catch (RepositoryException e) {
            log.error("Internal error. logging out.");
            logout();
            throw new ExecutionException("Error during login", e);
        }

        setProperty(KEY_WORKSPACE, session.getWorkspace().getName());
        setProperty(KEY_USER, session.getUserID());
        setProperty(KEY_PROMPT,
                "[${" + KEY_USER + "}@${" + KEY_WORKSPACE + "} ${" + KEY_PATH  +"}]$ ");

        log.info("Logged into repository as {} on workspace {}", session.getUserID(), session.getWorkspace().getName());
    }

    protected void logout() {
        if (session == null) {
            log.info("Not logged into repository");
            return;
        }
        if (isMounted()) {
            unmount();
        }
        session.logout();
        session = null;
        log.info("Logged out from repository.");

        // remove context
        console.switchContext(ctxPlatform);
        console.removeContext(ctxRepository);
        ctxRepository = null;
    }

    protected boolean isLoggedIn() {
        return session != null && session.isLive();
    }

    public VaultFileSystem getVaultFileSystem() {
        return fs;
    }

    protected void mount(String creds, String wsp, String root, String config,
                         String filter, boolean remount) {
        if (!isConnected()) {
            throw new ExecutionException("Not connected to repository.");
        }
        if (!isLoggedIn()) {
            login(creds, wsp);
        }
        if (isMounted()) {
            if (remount) {
                unmount();
            } else {
                log.info("Filesystem already mounted.");
                return;
            }
        }

        if (root == null) {
            root = getProperty(KEY_DEFAULT_MOUNTPOINT);
        }
        if (config == null) {
            config = getProperty(KEY_DEFAULT_CONFIG_XML);
        }
        if (filter == null) {
            filter = getProperty(KEY_DEFAULT_FILTER_XML);
        }
        try {
            StringBuffer uri = new StringBuffer(getProperty(KEY_DEFAULT_URI));
            uri.append("/").append(session.getWorkspace().getName());
            if (root != null && !"/".equals(root)) {
                uri.append(root);
            }
            RepositoryAddress mp =
                    new RepositoryAddress(uri.toString());
            log.info("Mounting JcrFs on {}", mp.toString());

            ExportRoot exportRoot = ExportRoot.findRoot(getPlatformFile("", true));
            MetaInf inf = exportRoot == null ? null : exportRoot.getMetaInf();

            // get config
            VaultFsConfig jcrfsConfig = null;
            if (config != null) {
                File configFile = new File(config);
                if (configFile.canRead()) {
                    jcrfsConfig = AbstractVaultFsConfig.load(configFile);
                    log.info("using {}", configFile.getCanonicalPath());
                }
            }
            if (jcrfsConfig == null && inf != null) {
                jcrfsConfig = inf.getConfig();
                if (jcrfsConfig != null) {
                    log.info("using config from {}", exportRoot.getMetaDir().getPath());
                }
            }
            if (jcrfsConfig == null) {
                log.info("using embeded default config");
            }
            // get workspace filter
            WorkspaceFilter wspFilter = null;
            if (filter != null) {
                File filterFile = new File(filter);
                if (filterFile.canRead()) {
                    wspFilter = new DefaultWorkspaceFilter();
                    ((DefaultWorkspaceFilter) wspFilter).load(filterFile);
                    log.info("using {}", filterFile.getCanonicalPath());
                }
            }
            if (wspFilter == null && inf != null) {
                wspFilter = inf.getFilter();
                if (wspFilter != null) {
                    log.info("using filter from {}", exportRoot.getMetaDir().getPath());
                }
            }
            if (wspFilter == null) {
                log.info("using embeded default filter");
            }
            fs = Mounter.mount(jcrfsConfig, wspFilter, mp, null, session);
        } catch (Exception e) {
            throw new ExecutionException("Unable to mount filesystem.", e);
        }

        try {
            // install aggregate context
            ctxAfct = new AggregateExecutionContext(this, "agg", fs.getAggregateManager().getRoot());
            console.addContext(ctxAfct);
        } catch (RepositoryException e) {
            log.error("Internal error during mount. unmounting.");
            try {
                fs.unmount();
            } catch (RepositoryException e1) {
                // ignore
            }
            fs = null;
            throw new ExecutionException("Error during mount.", e);
        }

        // install vault fs context
        ctxJcrfs = new VaultFsExecutionContext(this, "vlt", fs.getRoot());
        console.addContext(ctxJcrfs);

        setProperty(KEY_MOUNTPOINT, root);
        try {
            log.info("Filesystem mounted on {}{}", fs.getAggregateManager().getWorkspace(), root);
        } catch (RepositoryException e) {
            // ignore
        }
    }

    protected boolean isMounted() {
        return fs != null;
    }

    protected void unmount() {
        if (fs == null) {
            log.info("Filesystem not mounted.");
            return;
        }
        try {
            fs.unmount();
        } catch (RepositoryException e) {
            log.error("Error while unmounting filesystem (ignored)", e);
        }
        fs = null;
        setProperty(KEY_MOUNTPOINT, null);

        // remove context
        console.switchContext(ctxRepository);
        console.removeContext(ctxJcrfs);
        console.removeContext(ctxAfct);

        log.info("Filesystem unmounted");
    }

    protected VaultFile getVaultFile(String path, boolean mustExist) {
        return (VaultFile) ctxJcrfs.getFile(path, mustExist).unwrap();
    }

    protected File getPlatformFile(String path, boolean mustExist) {
        return (File) ctxPlatform.getFile(path, mustExist).unwrap();
    }

    protected List<File> getPlatformFiles(List<String> paths, boolean mustExist) {
        List<File> files = new ArrayList<File>(paths.size());
        for (String path: paths) {
            files.add((File) ctxPlatform.getFile(path, mustExist).unwrap());
        }
        return files;
    }

    protected Aggregate getArtifactsNode(String path, boolean mustExist) {
        return (Aggregate) ctxAfct.getFile(path, mustExist).unwrap();
    }


    protected void assertMounted() {
        if (fs == null) {
            throw new IllegalStateException("Filesystem not mounted.");
        }
    }

    protected void connect() {
        if (rep != null) {
            throw new ExecutionException("Already connected to " + getProperty(KEY_URI));
        } else {
            String uri = getProperty(KEY_DEFAULT_URI);
            ConnectionOptions options = ConnectionOptions.fromServiceFactoryParameters(getEnv());
            try {
                rep = repProvider.getRepository(new RepositoryAddress(uri), options);
                setProperty(KEY_URI, uri);
                StringBuffer info = new StringBuffer();
                info.append(rep.getDescriptor(Repository.REP_NAME_DESC)).append(' ');
                info.append(rep.getDescriptor(Repository.REP_VERSION_DESC));
                log.info("Connected to {} ({})", uri, info.toString());
            } catch (Exception e) {
                rep = null;
                throw new ExecutionException("Error while connecting to " + uri, e);
            }
        }
    }

    protected boolean isConnected() {
        return rep != null;
    }

    protected void disconnect() {
        if (rep == null) {
            log.info("Not connected to repository.");
        } else {
            if (isLoggedIn()) {
                logout();
            }
            rep = null;
            setProperty(KEY_URI, null);
            log.info("Disconnected.");
        }
    }

    public PomProperties getPomProperties() {
        return new PomProperties("org.apache.jackrabbit.vault", "vault-cli");
    }

    public String getApplicationName() {
        return "Jackrabbit FileVault";
    }

    public String getShellCommand() {
        return "vlt";
    }

    protected ExecutionContext getDefaultContext() {
        if (ctxDefault == null) {
            ctxDefault = new VltExecutionContext(this);
            ctxDefault.installCommand(new CmdConsole());
        }
        return ctxDefault;
    }

    public Console getConsole() {
        return console;
    }

    /**
     * {@inheritDoc}
     */
    protected void initLogging() {
        Log4JConfig.init(LOG4J_PROPERTIES);
    }

    /**
     * {@inheritDoc}
     */
    protected void init() {
        super.init();
        initLineSeparator();

        // init providers
        repProvider = new RepositoryProvider();
        confCredsProvider = new ConfigCredentialsStore();
        credentialsStore = new PasswordPromptingCredentialsStore(confCredsProvider);

        // setup default config
        setProperty(KEY_DEFAULT_CREDS, null);
        // read the default URI from the .vlt root if available
        File cwd;
        try {
            cwd = new File(".").getCanonicalFile();
        } catch (IOException e) {
            throw new ExecutionException(e);
        }
        ExportRoot exportRoot = ExportRoot.findRoot(cwd);
        RepositoryAddress mountpoint = null;
        if (exportRoot != null) {
            try {
                File dir = new File(exportRoot.getJcrRoot(), VltDirectory.META_DIR_NAME);
                MetaDirectory rootMeta = VltContext.createMetaDirectory(dir);
                String addr = rootMeta.getRepositoryUrl();
                if (addr != null) {
                    mountpoint = new RepositoryAddress(addr);
                }
            } catch (Exception e) {
                // ignore
            }
        }
        if (mountpoint == null) {
            setProperty(KEY_DEFAULT_URI, DEFAULT_URI);
        } else {
            setProperty(KEY_DEFAULT_URI, mountpoint.getSpecificURI().toString());
            setProperty(KEY_DEFAULT_WORKSPACE, mountpoint.getWorkspace());
            setProperty(KEY_DEFAULT_MOUNTPOINT, mountpoint.getPath());
        }
        setProperty(KEY_PROMPT, "$ ");

        // add platform context
        console = new VaultFsConsole(this);
        ctxPlatform = new PlatformExecutionContext(this, "local", cwd);
        console.addContext(ctxPlatform);
    }

    public CredentialsStore getCredentialsStore() {
        return credentialsStore;
    }

    /**
     * initializes the process wide line separator based on the {@code vlt.line.separator} system property. This helps
     * supporting windows batch files, where passing a different line separator is very difficult via script.
     *
     * <ul>
     * <li>if {@code vlt.line.separator} is {@code "LF"}, the java system property {@code line.separator} will be set to {@code "\n"}.</li>
     * <li>if {@code vlt.line.separator} is {@code "CRLF"}, the java system property {@code line.separator} will be set to {@code "\r\n"}.</li>
     * <li>if {@code vlt.line.separator} is missing, the system property will not be altered.</li>
     * </ul>
     */
    private static void initLineSeparator() {
        final String vltSep = System.getProperty("vlt.line.separator");
        if (vltSep != null) {
            if ("LF".equals(vltSep)) {
                System.setProperty("line.separator","\n");
            } else if ("CRLF".equals(vltSep)) {
                System.setProperty("line.separator","\r\n");
            } else {
                log.warn("Warning, invalid vtl.line.separator value '{}' ignored", vltSep);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void close() {
        if (isConnected()) {
            disconnect();
        }
    }

    //------------------------------------------------------------< Command >---


    public GroupBuilder addApplicationOptions(GroupBuilder gbuilder) {
        /*
        optURI = new DefaultOptionBuilder()
                .withLongName("uri")
                .withDescription("The rmi uri in the format '//<host>:<port>/<name>'.")
                .withArgument(new ArgumentBuilder()
                        .withDescription("defaults to '" + DEFAULT_RMIURI + "'")
                        .withMinimum(0)
                        .withMaximum(1)
                        .create()
                )
                .create();

        optWorkspace = new DefaultOptionBuilder()
                .withLongName("workspace")
                .withDescription("The default workspace to connect to.")
                .withArgument(new ArgumentBuilder()
                        .withDescription("If missing the default workspace is used.")
                        .withMinimum(0)
                        .withMaximum(1)
                        .create()
                )
                .create();
        */
        optCreds = new DefaultOptionBuilder()
                .withLongName("credentials")
                .withDescription("The default credentials to use")
                .withArgument(new ArgumentBuilder()
                        .withDescription("Format: <user:pass>. If missing an anonymous login is used. " +
                                "If the password is not specified it is prompted via console.")
                        .withMinimum(0)
                        .withMaximum(1)
                        .create()
                )
                .create();
        optUpdateCreds = new DefaultOptionBuilder()
                .withLongName("update-credentials")
                .withDescription("if present the credentials-to-host list is updated in the ~/.vault/auth.xml")
                .create();
        /*
        optMountpoint = new DefaultOptionBuilder()
                .withLongName("mountpoint")
                .withDescription("The default mountpoint to use")
                .withArgument(new ArgumentBuilder()
                        .withDescription("If missing, the root node is used as mountpoint.")
                        .withMinimum(0)
                        .withMaximum(1)
                        .create()
                )
                 .create();
        */
        optConfig = new DefaultOptionBuilder()
                .withLongName("config")
                .withDescription("The JcrFs config to use")
                .withArgument(new ArgumentBuilder()
                        .withDescription("If missing the default config is used.")
                        .withMinimum(0)
                        .withMaximum(1)
                        .create()
                )
                .create();
        optUseSystemProperties = new DefaultOptionBuilder()
                .withLongName("useSystemProperties")
                .withDescription("Evaluating the default Java system properties for connection settings.")
                .create();
        optAllowSelfSignedCertificate = new DefaultOptionBuilder()
                .withLongName("allowSelfSignedCertificates")
                .withDescription("Allows to connect to HTTPS repository urls whose certificate is self-signed.")
                .create();
        optDisableHostnameValidation = new DefaultOptionBuilder()
                .withLongName("disableHostnameValidator")
                .withDescription("Disables hostname validation for HTTPS repository urls.")
                .create();
        optConnectionTimeoutMs = new DefaultOptionBuilder()
                .withLongName("connectionTimeoutMs")
                .withDescription("The connection timeout in milliseconds.")
                .withArgument(new ArgumentBuilder()
                        .withMinimum(1)
                        .withMaximum(1)
                        .create()
                )
                .create();
        optRequestTimeoutMs = new DefaultOptionBuilder()
                .withLongName("requestTimeoutMs")
                .withDescription("The request timeout in milliseconds.")
                .withArgument(new ArgumentBuilder()
                        .withMinimum(1)
                        .withMaximum(1)
                        .create()
                )
                .create();
        optSocketTimeoutMs = new DefaultOptionBuilder()
                .withLongName("socketTimeoutMs")
                .withDescription("The socket timeout in milliseconds.")
                .withArgument(new ArgumentBuilder()
                        .withMinimum(1)
                        .withMaximum(1)
                        .create()
                )
                .create();
        optProxyHost = new DefaultOptionBuilder()
                .withLongName("proxyHost")
                .withDescription("The host of the proxy to use.")
                .withArgument(new ArgumentBuilder()
                        .withMinimum(1)
                        .withMaximum(1)
                        .create()
                )
                .create();
        optProxyPort = new DefaultOptionBuilder()
                .withLongName("proxyPort")
                .withDescription("The port where the proxy is running (requires proxyHost as well).")
                .withArgument(new ArgumentBuilder()
                        .withMinimum(1)
                        .withMaximum(1)
                        .create()
                )
                .create();
        optProxyProtocol = new DefaultOptionBuilder()
                .withLongName("proxyProtocol")
                .withDescription("The protocol for which to use the proxy (requires proxyHost as well). If not set proxy is used for both HTTP and HTTPS.")
                .withArgument(new ArgumentBuilder()
                        .withMinimum(1)
                        .withMaximum(1)
                        .create()
                )
                .create();
        optProxyUsername = new DefaultOptionBuilder()
                .withLongName("proxyUsername")
                .withDescription("The username to use for authentication at the proxy (requires proxyHost as well).")
                .withArgument(new ArgumentBuilder()
                        .withMinimum(1)
                        .withMaximum(1)
                        .create()
                )
                .create();
        optProxyPassword = new DefaultOptionBuilder()
                .withLongName("proxyPassword")
                .withDescription("The password to use for authentication at the proxy (requires proxyUsername as well)")
                .withArgument(new ArgumentBuilder()
                        .withMinimum(1)
                        .withMaximum(1)
                        .create()
                )
                .create();
        // register extended options
        for (ExtendedOption x: xOpts) {
            gbuilder.withOption(x.getOption());
        }
        //gbuilder.withOption(optURI);
        //gbuilder.withOption(optWorkspace);
        gbuilder.withOption(optCreds);
        gbuilder.withOption(optUpdateCreds);
        //gbuilder.withOption(optMountpoint);
        gbuilder.withOption(optConfig);
        gbuilder.withOption(optUseSystemProperties);
        gbuilder.withOption(optAllowSelfSignedCertificate);
        gbuilder.withOption(optDisableHostnameValidation);
        gbuilder.withOption(optConnectionTimeoutMs);
        gbuilder.withOption(optRequestTimeoutMs);
        gbuilder.withOption(optSocketTimeoutMs);
        gbuilder.withOption(optProxyHost).withOption(optProxyPort).withOption(optProxyProtocol).withOption(optProxyUsername).withOption(optProxyPassword);
        return super.addApplicationOptions(gbuilder);
    }

    public void execute(CommandLine commandLine) throws ExecutionException {
        // TODO: move extended options support the commons-cli2
        for (ExtendedOption x: xOpts) {
            if (commandLine.hasOption(x.getOption())) {
                List l = commandLine.getValues(x.getOption());
                if (l.isEmpty()) {
                    CliHelpFormatter fmt = CliHelpFormatter.create();
                    fmt.setCmd(x);
                    fmt.setShowUsage(false);
                    fmt.print();
                    return;
                } else {
                    x.process(l.get(0).toString());
                }
            }
        }
        super.execute(commandLine);
    }

    public void prepare(CommandLine cl) throws ExecutionException {
        super.prepare(cl);
        /*
        if (cl.getValue(optURI) != null) {
            setProperty(KEY_DEFAULT_RMIURI, (String) cl.getValue(optURI));
        }
        if (cl.getValue(optWorkspace) != null) {
            setProperty(KEY_DEFAULT_WORKSPACE, (String) cl.getValue(optWorkspace));
        }
        if (cl.getValue(optMountpoint) != null) {
            setProperty(KEY_DEFAULT_MOUNTPOINT, (String) cl.getValue(optMountpoint));
        }
        */
        if (cl.getValue(optCreds) != null) {
            String userPass = (String) cl.getValue(optCreds);
            setProperty(KEY_DEFAULT_CREDS, userPass);
            confCredsProvider.setCredentials(userPass);
            confCredsProvider.setStoreEnabled(cl.hasOption(optUpdateCreds));
        }
        if (cl.getValue(optConfig) != null) {
            setProperty(KEY_DEFAULT_CONFIG_XML, (String) cl.getValue(optConfig));
        }
        
        parseConnectionOptions(cl);
    }

    private void parseConnectionOptions(CommandLine cl) {
        ConnectionOptions.Builder builder = ConnectionOptions.builder();
        builder.allowSelfSignedCertificates(cl.hasOption(optAllowSelfSignedCertificate));
        builder.disableHostnameVerification(cl.hasOption(optDisableHostnameValidation));
        builder.connectionTimeoutMs(Integer.parseInt(cl.getValue(optConnectionTimeoutMs, -1).toString()));
        builder.requestTimeoutMs(Integer.parseInt(cl.getValue(optRequestTimeoutMs, -1).toString()));
        builder.socketTimeoutMs(Integer.parseInt(cl.getValue(optSocketTimeoutMs, -1).toString()));
        builder.useSystemProperties(cl.hasOption(optUseSystemProperties));
        if (cl.getValue(optProxyHost) != null) {
            builder.proxyHost(cl.getValue(optProxyHost).toString());
            builder.proxyPort(Integer.parseInt(cl.getValue(optProxyPort, -1).toString()));
            if (cl.getValue(optProxyProtocol) != null) {
                builder.proxyProtocol(cl.getValue(optProxyProtocol).toString());
            }
            if (cl.getValue(optProxyUsername) != null) {
                builder.proxyUsername(cl.getValue(optProxyUsername).toString());
            }
            if (cl.getValue(optProxyPassword) != null) {
                builder.proxyPassword(cl.getValue(optProxyPassword).toString());
            }
        }
        Map<String, String> options = builder.build().toServiceFactoryParameters();
        for (Map.Entry<String, String> entry : options.entrySet()) {
            setProperty(entry.getKey(), entry.getValue());
        }
    }


    private static class PasswordPromptingCredentialsStore implements CredentialsStore {

        private CredentialsStore base;

        private PasswordPromptingCredentialsStore(CredentialsStore base) {
            this.base = base;
        }

        public Credentials getCredentials(RepositoryAddress mountpoint) {
            Credentials creds = base.getCredentials(mountpoint);
            if (creds instanceof SimpleCredentials) {
                try {
                    SimpleCredentials simpleCredentials = (SimpleCredentials) creds;
                    if (simpleCredentials.getPassword().length == 0) {
                        System.out.printf("Please enter password for user %s connecting to %s: ",
                                simpleCredentials.getUserID(), mountpoint);
                        String password = new jline.ConsoleReader().readLine('*');
                        creds = new SimpleCredentials(simpleCredentials.getUserID(), password.toCharArray());
                    }
                } catch (IOException e) {
                    log.error("Error while opening console for reading password" + e);
                }
            }
            return creds;
        }

        public void storeCredentials(RepositoryAddress mountpoint, Credentials creds) {
            base.storeCredentials(mountpoint, creds);
        }
    }

}
