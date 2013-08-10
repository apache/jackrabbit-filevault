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
package org.apache.jackrabbit.vault.util.console.util;

import java.net.URL;

import org.apache.jackrabbit.vault.util.console.ExecutionException;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;

/**
 * <code>Log4JConfig</code>...
 */
public class Log4JConfig {

    public static void init(String log4jConfig) {
        URL url = Log4JConfig.class.getResource(log4jConfig);
        PropertyConfigurator.configure(url);
    }

    public static void setLevel(String level) {
        Level l = Level.toLevel(level);
        if (l == null) {
            throw new ExecutionException("Invalid log level " + level);
        }
        LogManager.getRootLogger().setLevel(l);
    }

    public static String getLevel() {
        return LogManager.getRootLogger().getLevel().toString();
    }
}