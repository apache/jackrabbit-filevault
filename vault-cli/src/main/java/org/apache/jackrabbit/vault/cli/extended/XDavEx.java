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
package org.apache.jackrabbit.vault.cli.extended;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.jackrabbit.vault.util.console.ExecutionContext;

/**
 * {@code XDavEx}...
 */
public class XDavEx extends ExtendedOption {

    public boolean execute(ExecutionContext executionContext, CommandLine commandLine)
            throws Exception {
        return false;
    }

    public String getSystemPrefix() {
        return "jcr.remoting.";
    }

    public String getName() {
        return "Xdavex";
    }

    public String getShortDescription() {
        return "Extended JCR remoting options (omit argument for help)";
    }

    public String getLongDescription() {
        return  "Configures extended options for the JCR remoting protocol.";
    }

    public String getExample() {
        return "Set depth to 2 and specify a log: -Xdavex:depth=2,spilog=my.log\n" +
               "     Set an empty referer header: -Xdavex:referer=,depth=2\n" +
               "   Set a specific referer header: -Xdavex:referer=http://my.server.com";
    }

    public boolean hasName(String s) {
        return false;
    }

    public Option getCommand() {
        return getOption();
    }
}