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

import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;

/**
 * Command Line Command
 */
public interface CliCommand {

    /**
     * verbose option that can be globally used
     */
    Option OPT_VERBOSE = new DefaultOptionBuilder()
            .withShortName("v")
            .withLongName("verbose")
            .withDescription("verbose output")
            .create();

    /**
     * quiet option that can be globally used
     */
    Option OPT_QUIET = new DefaultOptionBuilder()
            .withShortName("q")
            .withLongName("quiet")
            .withDescription("print as little as possible")
            .create();

    boolean execute(ExecutionContext ctx, CommandLine cl) throws Exception;

    String getName();

    String getShortDescription();

    String getLongDescription();

    String getExample();

    boolean hasName(String name);

    Option getCommand();
    
}