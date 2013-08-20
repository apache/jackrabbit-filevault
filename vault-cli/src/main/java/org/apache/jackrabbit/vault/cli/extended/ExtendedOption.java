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

import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.jackrabbit.vault.util.Text;
import org.apache.jackrabbit.vault.util.console.CliCommand;

/**
 * <code>ExtendedOption</code>...
 */
abstract public class ExtendedOption implements CliCommand {

    abstract public String getSystemPrefix();

    private final Option option = new DefaultOptionBuilder()
                .withShortName(getName())
                .withDescription(getShortDescription())
                .withArgument(new ArgumentBuilder()
                        .withInitialSeparator(':')
                        .withSubsequentSeparator((char)0)
                        .withMaximum(1)
                        .create()
                )
                .create();
    
    public void process(String args) {
        // currently just split the args
        for (String nvp: Text.explode(args, ',')) {
            String[] nv = Text.explode(nvp, '=', true);
            String name = nv[0].trim();
            String value = nv.length > 1 ? nv[1].trim() : "true";
            System.setProperty(getSystemPrefix() + name, value);
        }
    }

    public Option getOption() {
        return option;
    }
}