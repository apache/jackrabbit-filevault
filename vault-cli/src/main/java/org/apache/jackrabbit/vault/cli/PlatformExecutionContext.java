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

import org.apache.jackrabbit.vault.util.console.commands.CmdCtx;
import org.apache.jackrabbit.vault.util.console.platform.CmdCd;
import org.apache.jackrabbit.vault.util.console.platform.CmdLs;
import org.apache.jackrabbit.vault.util.console.platform.PlatformFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a console/shell that operates on a jcrfs.
 *
 */
public class PlatformExecutionContext extends VaultFsConsoleExecutionContext {

    protected static Logger log = LoggerFactory.getLogger(PlatformExecutionContext.class);

    public PlatformExecutionContext(VaultFsApp app, String name, File rootFile) {
        super(app, name);
        installCommand(new CmdCd());
        installCommand(new CmdCtx());
        installCommand(new CmdConnect());
        installCommand(new CmdDisconnect());
        installCommand(new CmdLogin());
        installCommand(new CmdLogout());
        installCommand(new CmdLs());
        installCommand(new CmdTree());
        installCommand(new CmdDump());
        installCommand(new CmdDebug());
        // vlt commands
        installCommand(new CmdCheckout());        
        installCommand(new CmdStatus());
        installCommand(new CmdCommit());
        installCommand(new CmdUpdate());
        installCommand(new CmdInfo());
        installCommand(new CmdRevert());
        installCommand(new CmdResolved());
        installCommand(new CmdPropSet());
        installCommand(new CmdPropGet());
        installCommand(new CmdPropList());
        installCommand(new CmdAdd());
        installCommand(new CmdDelete());
        installCommand(new CmdDiff());

        setFileSystem(new PlatformFile(rootFile));
    }

}