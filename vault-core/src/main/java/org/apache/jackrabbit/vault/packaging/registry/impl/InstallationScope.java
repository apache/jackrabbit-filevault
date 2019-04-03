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
package org.apache.jackrabbit.vault.packaging.registry.impl;

import java.util.Arrays;

public class InstallationScope {
    public final static String UNSCOPED = "unscoped";
    public final static String APPLICATION_SCOPED = "application";
    public final static String CONTENT_SCOPED = "content";
    
    private final static String[] OPTIONS = new String[] {UNSCOPED, APPLICATION_SCOPED, CONTENT_SCOPED};
    private static String scope;
    
    public InstallationScope(String scope) {
        if (Arrays.asList(OPTIONS).contains(scope)) {
            InstallationScope.scope = scope;
        } else {
            throw new IllegalArgumentException(String.format("Value %s unsupported", scope)) ;
        }
    }

    public String getScope() {
        return scope;
    }

}
