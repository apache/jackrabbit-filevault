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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

/**
 * Command Line Command
 */
// Note: switched from commons-cli2 to commons-cli (1.x)
public interface CliCommand {

    /**
     * verbose option that can be globally used
     */
    Option OPT_VERBOSE = Option.builder("v")
            .longOpt("verbose")
            .desc("verbose output")
            .build();

    /**
     * quiet option that can be globally used
     */
    Option OPT_QUIET = Option.builder("q")
            .longOpt("quiet")
            .desc("print as little as possible")
            .build();

    boolean execute(ExecutionContext ctx, CommandLine cl) throws Exception;

    String getName();

    String getShortDescription();

    String getLongDescription();

    String getExample();

    boolean hasName(String name);

    /**
     * Returns the Commons CLI Options that describe the command.
     */
    Options getOptions();

    void printHelp();

}