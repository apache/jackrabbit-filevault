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

import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.jackrabbit.vault.util.console.ExecutionContext;

/**
 * <code>XJcrLog</code>...
 */
public class XJcrLog extends ExtendedOption {

    public boolean execute(ExecutionContext executionContext, CommandLine commandLine)
            throws Exception {
        return false;
    }

    public String getSystemPrefix() {
        return "jcrlog.";
    }
    
    public String getName() {
        return "Xjcrlog";
    }

    public String getShortDescription() {
        return "Extended JcrLog options (omit argument for help)";
    }

    public String getLongDescription() {
        return  "Enables and controls JcrLog. JcrLog dumps all calls through the\n" +
                "JCR API to a file and/or stdout.";
    }

    public String getExample() {
        return "-Xjcrlog:sysout,retrun,file=my.log";
    }

    public boolean hasName(String s) {
        return false;
    }

    public Option getCommand() {
        return new CommandBuilder()
                .withName("Xjcrlog")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(new CommandBuilder()
                                .withName("sysout")
                                .withDescription("enable logging to the stdout")
                                .create())
                        .withOption(new ArgumentBuilder()
                                .withName("file=<file>")
                                .withDescription("log to the specified file")
                                .withMinimum(1)
                                .withMaximum(1)
                                .create())
                        .withOption(new CommandBuilder()
                                .withName("return")
                                .withDescription("enable logging of return values")
                                .create())
                        .withOption(new CommandBuilder()
                                .withName("caller")
                                .withDescription("enable logging of the caller")
                                .create())
                        .withOption(new CommandBuilder()
                                .withName("exception")
                                .withDescription("enable logging of exceptions")
                                .create())
                        .withOption(new CommandBuilder()
                                .withName("stream")
                                .withDescription("enable logging of streams")
                                .create())
                        /*
                        .withOption(new CommandBuilder()
                                .withName("cast")
                                .withDescription("cast to real API")
                                .create())
                        */
                        .create()
                )
                .create();
    }
}