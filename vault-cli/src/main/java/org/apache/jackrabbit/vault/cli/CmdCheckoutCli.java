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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.util.console.ExecutionException;
import org.apache.jackrabbit.vault.vlt.VltContext;
import org.apache.jackrabbit.vault.vlt.actions.Checkout;

/**
 * Implements the 'checkout' command. there are 2 forms, either:
 * co rmi://localhost/crx.default/apps/components
 *
 * or:
 * co --mountpoint=rmi://localhost/crx.default /apps/components
 *
 *
 */
public class CmdCheckoutCli extends AbstractVaultCommand {

    private Option optForce;
    private Option optFilter;
    private Option argLocalPath;
    private Option argJcrPath;
    private Option argMountpoint;
    private Options options;

    @Override
    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl)
            throws Exception {
        throw new ExecutionException("internal error. command not supported in console");
    }

    protected void doExecute(VaultFsApp app, CommandLine cl) throws Exception {
        String jcrPath = cl.getOptionValue("jcrPath");
        String localPath = cl.getOptionValue("localPath");
        String root = cl.getOptionValue("uri");
        RepositoryAddress addr = new RepositoryAddress(root);

        // shift arguments
        if (localPath == null) {
            localPath = jcrPath;
            jcrPath = null;
        }
        if (jcrPath == null) {
            jcrPath = addr.getPath();
            addr = addr.resolve("/");
        }
        if (localPath == null) {
            localPath = Text.getName(jcrPath);
        }
        if (jcrPath.length() == 0 || !jcrPath.startsWith("/")) {
            throw new ExecutionException("JCR path needs to be absolute: " + jcrPath);
        }
        File localFile = app.getPlatformFile(localPath, false);
        if (localFile.isFile()) {
            throw new ExecutionException("Local file must be a directory: " + localFile.getPath());
        }
        if (!localFile.exists()) {
            localFile.mkdir();
        }
        VltContext vCtx = app.createVaultContext(localFile);
        vCtx.setVerbose(cl.hasOption(OPT_VERBOSE.getOpt()));
        vCtx.setQuiet(cl.hasOption(OPT_QUIET.getOpt()));
        vCtx.setDefaultFilter(cl.getOptionValue("file"));
        Checkout c = new Checkout(addr, jcrPath, localFile);
        c.setForce(cl.hasOption("force"));
        vCtx.execute(c);
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Checkout a Vault file system";
    }

    public String getLongDescription() {
        return  "Checkout the Vault file system (starting at <uri> to the " +
                "local filesystem at <local-path>.\n" +
                "A <jcrPath> argument can be provided to checkout a sub directory " +
                "of the remote tree.\n\n" +
                "A workspace filters can be specified that will be copied into the " +
                "META-INF directory.\n\n" +
                "Examples:\n" +
                "\n" +
                "Using the JCR Remoting:\n" +
                "  vlt --credentials admin:admin co http://localhost:8080/crx/server/crx.default/jcr:root/\n" +
                "  \n" +
                "with default workspace:\n" +
                "  vlt --credentials admin:admin co http://localhost:8080/crx/server/-/jcr:root/\n" +
                "  \n" +
                "if URI is incomplete, it will be expanded:\n" +
                "  vlt --credentials admin:admin co http://localhost:8080/crx\n";
    }

    public CmdCheckoutCli() {
        options = new Options();
        options.addOption(OPT_VERBOSE);
        options.addOption(OPT_QUIET);
        optForce = Option.builder()
                .longOpt("force")
                .desc("force checkout to overwrite local files if they already exist.")
                .build();
        options.addOption(optForce);
        optFilter = Option.builder("f")
                .longOpt("filter")
                .desc("specifies auto filters if none defined.")
                .hasArg()
                .argName("file")
                .build();
        options.addOption(optFilter);
        argMountpoint = Option.builder()
                .argName("uri")
                .desc("mountpoint uri")
                .hasArg()
                .build();
        options.addOption(argMountpoint);
        argJcrPath = Option.builder()
                .argName("jcrPath")
                .desc("remote path (optional)")
                .hasArg()
                .build();
        options.addOption(argJcrPath);
        argLocalPath = Option.builder()
                .argName("localPath")
                .desc("local path (optional)")
                .hasArg()
                .build();
        options.addOption(argLocalPath);
    }

    public Options getOptions() {
        return options;
    }
}