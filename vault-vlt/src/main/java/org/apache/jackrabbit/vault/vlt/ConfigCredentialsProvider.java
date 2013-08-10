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
public class ConfigCredentialsProvider extends DefaultCredentialsProvider {

    protected static Logger log = LoggerFactory.getLogger(ConfigCredentialsProvider.class);

    private VaultAuthConfig config;

    public ConfigCredentialsProvider(CredentialsProvider base) {
        super(base);
        config = new VaultAuthConfig();
        try {
            config.load();
        } catch (IOException e) {
            log.error("Error while loading auth configuration: {} ", e.toString());
        } catch (ConfigurationException e) {
            log.error("Error while loading auth configuration: {} ", e.toString());
        }
    }

    public Credentials getCredentials(RepositoryAddress mountpoint) {
        // check if temporary creds are set
        if (super.getCredentials(mountpoint) != null) {
            return super.getCredentials(mountpoint);
        }
        Credentials creds = fetchCredentials(mountpoint);
        return creds == null
                ? super.getCredentials(mountpoint)
                : creds;
    }

    private Credentials fetchCredentials(RepositoryAddress mountpoint) {
        VaultAuthConfig.RepositoryConfig cfg = config.getRepoConfig(
                getLookupId(mountpoint)
        );
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
        VaultAuthConfig.RepositoryConfig cfg  = new VaultAuthConfig.RepositoryConfig(
                getLookupId(mountpoint)
        );
        cfg.addCredsConfig(new SimpleCredentialsConfig(((SimpleCredentials) creds)));
        config.addRepositoryConfig(cfg);
        try {
            config.save();
        } catch (IOException e) {
            log.error("Error while saving auth configuration: {} ", e.toString());
        }
    }
}