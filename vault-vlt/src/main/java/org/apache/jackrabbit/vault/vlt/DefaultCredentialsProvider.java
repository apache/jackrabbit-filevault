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
package org.apache.jackrabbit.vault.vlt;

import javax.jcr.Credentials;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;

/**
 * <code>CredentialsProvider</code>...
 *
 */
public class DefaultCredentialsProvider implements CredentialsProvider {

    private Credentials creds;

    private CredentialsProvider base;

    public DefaultCredentialsProvider() {
    }

    public DefaultCredentialsProvider(CredentialsProvider base) {
        this.base = base;
    }

    public void setDefaultCredentials(String userPass) {
        if (userPass != null) {
            int idx = userPass.indexOf(':');
            if (idx > 0) {
                creds = new SimpleCredentials(userPass.substring(0, idx), userPass.substring(idx + 1).toCharArray());
            } else {
                creds = new SimpleCredentials(userPass, new char[0]);
            }
        } else {
            creds = null;
        }
    }

    public Credentials getCredentials(RepositoryAddress mountpoint) {
        return creds == null && base != null
                ? base.getCredentials(mountpoint)
                : creds;
    }

    public void storeCredentials(RepositoryAddress mountpoint, Credentials creds) {
        this.creds = creds;
    }
}