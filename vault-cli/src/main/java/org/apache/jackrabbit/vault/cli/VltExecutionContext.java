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

import org.apache.jackrabbit.vault.util.console.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a console/shell that operates on a jcrfs.
 *
 */
public class VltExecutionContext extends ExecutionContext {

    protected static Logger log = LoggerFactory.getLogger(VltExecutionContext.class);

    private final VaultFsApp app;

    public VltExecutionContext(VaultFsApp app) {
        super(app);
        this.app = app;
        installCommand(new CmdExportCli());
        installCommand(new CmdImportCli());
        installCommand(new CmdCheckoutCli());
        installCommand(new CmdStatus());
        installCommand(new CmdUpdate());
        installCommand(new CmdInfo());
        installCommand(new CmdCommit());
        installCommand(new CmdRevert());
        installCommand(new CmdResolved());
        installCommand(new CmdPropGet());
        installCommand(new CmdPropList());
        installCommand(new CmdPropSet());
        installCommand(new CmdAdd());
        installCommand(new CmdDelete());
        installCommand(new CmdDiff());
        installCommand(new CmdRcp());
        installCommand(new CmdSync());

        //installCommand(new CmdVaultDebug());
    }

    public VaultFsApp getJcrFsApp() {
        return app;
    }

}