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

import java.io.IOException;

import javax.jcr.Credentials;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.SimpleCredentialsConfig;
import org.apache.jackrabbit.vault.fs.config.VaultAuthConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>CredentialsProvider</code>...
 *
 */
public class ConfigCredentialsStore implements CredentialsStore {

    protected static Logger log = LoggerFactory.getLogger(ConfigCredentialsStore.class);

    private VaultAuthConfig config;

    private Credentials defaultCreds;

    private Credentials credentials;

    private boolean storeEnabled;

    public ConfigCredentialsStore() {
        config = new VaultAuthConfig();
        try {
            config.load();
        } catch (IOException e) {
            log.error("Error while loading auth configuration: {} ", e.toString());
        } catch (ConfigurationException e) {
            log.error("Error while loading auth configuration: {} ", e.toString());
        }
    }

    public void setDefaultCredentials(String userPass) {
        this.defaultCreds = fromUserPass(userPass);
    }

    public void setCredentials(String userPass) {
        this.credentials = fromUserPass(userPass);
    }

    private static Credentials fromUserPass(String userPass) {
        if (userPass != null) {
            int idx = userPass.indexOf(':');
            if (idx > 0) {
                return new SimpleCredentials(userPass.substring(0, idx), userPass.substring(idx + 1).toCharArray());
            } else {
                return new SimpleCredentials(userPass, new char[0]);
            }
        }
        return null;
    }

    public Credentials getCredentials(RepositoryAddress mountpoint) {
        if (credentials != null) {
            return credentials;
        }
        Credentials creds = fetchCredentials(mountpoint);
        return creds == null
                ? defaultCreds
                : creds;
    }

    private Credentials fetchCredentials(RepositoryAddress mountpoint) {
        VaultAuthConfig.RepositoryConfig cfg = config.getRepoConfig(getLookupId(mountpoint));
        if (cfg == null) {
            return null;
        }
        return cfg.getCredsConfig().getCredentials();
    }

    private String getLookupId(RepositoryAddress mountpoint) {
        return mountpoint.getSpecificURI() + "/" + mountpoint.getWorkspace();
    }
    
    public void storeCredentials(RepositoryAddress mountpoint, Credentials creds) {
        if (!(creds instanceof SimpleCredentials)) {
            if (creds != null) {
                log.error("Unable to store non-simple credentials of type " + creds.getClass().getName());
            }
            return;
        }
        Credentials currentCreds = fetchCredentials(mountpoint);
        if (creds.equals(currentCreds)) {
            // don't update if already stored
            return;
        }

        SimpleCredentials simpleCredentials = (SimpleCredentials) creds;
        if (storeEnabled ||
                "admin".equals(simpleCredentials.getUserID()) && "admin".equals(new String(simpleCredentials.getPassword()))) {
            VaultAuthConfig.RepositoryConfig cfg  = new VaultAuthConfig.RepositoryConfig(getLookupId(mountpoint));
            cfg.addCredsConfig(new SimpleCredentialsConfig(simpleCredentials));
            config.addRepositoryConfig(cfg);
            try {
                config.save();
                log.warn("Credentials for {} updated in {}.", mountpoint, config.getConfigFile().getPath());
            } catch (IOException e) {
                log.error("Error while saving auth configuration: {} ", e.toString());
            }
        }
    }

    public void setStoreEnabled(boolean storeEnabled) {
        this.storeEnabled = storeEnabled;
    }
}