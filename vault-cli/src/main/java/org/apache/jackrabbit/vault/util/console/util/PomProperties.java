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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * <code>PomProperties</code>...
 */
public class PomProperties {

    public static String PROPS_PREFIX = "META-INF/maven/";

    public static String PROPS_SUFFIX = "/pom.properties";

    private final String groupId;

    private final String artifactId;

    private String pomPropsPath;

    private Properties props;

    public PomProperties(String groupId, String artifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        pomPropsPath = PROPS_PREFIX + groupId + "/" + artifactId + PROPS_SUFFIX;
    }

    public Properties getProperties() {
        if (props == null) {
            props = new Properties();
            try {
                InputStream in = PomProperties.class.getClassLoader().getResourceAsStream(pomPropsPath);
                if (in != null) {
                    props.load(in);
                    in.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }
        return props;
    }

    public String getVersion() {
        return getProperties().getProperty("version", "0.0.0");
    }
}