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
import java.util.List;

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.jackrabbit.vault.vlt.VltContext;
import org.apache.jackrabbit.vault.vlt.actions.PropSet;

/**
 * Implements the 'propset' command.
 */
public class CmdPropSet extends AbstractVaultCommand {

    private Option optRecursive;
    private Argument argLocalPath;
    private Argument argPropName;
    private Argument argPropValue;

    @SuppressWarnings("unchecked")
    protected void doExecute(VaultFsApp app, CommandLine cl) throws Exception {
        List<String> localPaths = cl.getValues(argLocalPath);
        List<File> localFiles = app.getPlatformFiles(localPaths, false);
        File localDir = app.getPlatformFile("", true);

        VltContext vCtx = app.createVaultContext(localDir);
        vCtx.setQuiet(cl.hasOption(OPT_QUIET));
        PropSet a = new PropSet(localDir,
                localFiles,
                !cl.hasOption(optRecursive),
                (String) cl.getValue(argPropName),
                (String) cl.getValue(argPropValue));
        vCtx.execute(a);
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Set the value of a property on files or directories.";
    }

    public String getLongDescription() {
        return "Note: vlt recognizes the following special versioned properties:\n\n" +
                "vlt:mime-type\n" +
                "  The mimetype of the file.  Used to determine whether to merge\n" +
                "  the file. A mimetype beginning with 'text/' (or an absent mimetype)\n" +
                "  is treated as text. Anything else is treated as binary.";
    }

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("propset")
                .withName("ps")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(OPT_QUIET)
                        .withOption(optRecursive = new DefaultOptionBuilder()
                                .withShortName("R")
                                .withLongName("recursive")
                                .withDescription("descend recursively")
                                .create())
                        .withOption(argPropName = new ArgumentBuilder()
                                .withName("propname")
                                .withDescription("the property name")
                                .withMinimum(1)
                                .withMaximum(1)
                                .create())
                        .withOption(argPropValue = new ArgumentBuilder()
                                .withName("propval")
                                .withDescription("the property value")
                                .withMinimum(1)
                                .withMaximum(1)
                                .create())
                        .withOption(argLocalPath = new ArgumentBuilder()
                                .withName("file")
                                .withDescription("file or directory to set the property to")
                                .withMinimum(1)
                                .create())
                        .create()
                )
                .create();
    }
}