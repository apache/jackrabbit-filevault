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
package org.apache.jackrabbit.vault.it;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.core.data.FileDataStore;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.plugins.blob.datastore.DataStoreBlobStore;
import org.apache.jackrabbit.oak.plugins.tree.impl.RootProviderService;
import org.apache.jackrabbit.oak.plugins.tree.impl.TreeProviderService;
import org.apache.jackrabbit.oak.security.internal.SecurityProviderBuilder;
import org.apache.jackrabbit.oak.security.internal.SecurityProviderHelper;
import org.apache.jackrabbit.oak.security.user.RandomAuthorizableNodeName;
import org.apache.jackrabbit.oak.segment.SegmentNodeStore;
import org.apache.jackrabbit.oak.segment.SegmentNodeStoreBuilders;
import org.apache.jackrabbit.oak.segment.file.FileStore;
import org.apache.jackrabbit.oak.segment.file.FileStoreBuilder;
import org.apache.jackrabbit.oak.segment.file.InvalidFileStoreVersionException;
import org.apache.jackrabbit.oak.spi.blob.BlobStore;
import org.apache.jackrabbit.oak.spi.mount.Mounts;
import org.apache.jackrabbit.oak.spi.security.ConfigurationParameters;
import org.apache.jackrabbit.oak.spi.security.SecurityProvider;
import org.apache.jackrabbit.oak.spi.security.authorization.AuthorizationConfiguration;
import org.apache.jackrabbit.oak.spi.security.authorization.cug.impl.CugConfiguration;
import org.apache.jackrabbit.oak.spi.security.authorization.principalbased.impl.PrincipalBasedAuthorizationConfiguration;
import org.apache.jackrabbit.oak.spi.security.principal.EveryonePrincipal;
import org.apache.jackrabbit.oak.spi.security.privilege.PrivilegeConstants;
import org.apache.jackrabbit.oak.spi.security.user.AuthorizableNodeName;
import org.apache.jackrabbit.oak.spi.security.user.UserConfiguration;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
import org.apache.jackrabbit.oak.spi.security.user.action.AccessControlAction;
import org.apache.jackrabbit.oak.spi.xml.ImportBehavior;
import org.apache.jackrabbit.oak.spi.xml.ProtectedItemImporter;
import org.apache.jackrabbit.vault.integration.support.RepositoryProvider;
import org.apache.jackrabbit.vault.integration.support.RepositoryProviderHelper;
import org.kohsuke.MetaInfServices;
import org.osgi.util.converter.Converters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

@MetaInfServices
public class OakRepositoryProvider implements RepositoryProvider {

    private static final String KEY_FILESTORE = "filestore";


    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(OakRepositoryProvider.class);

    
    private static final File DIR_OAK_REPO_HOME = new File("target", "repository-oak-" + System.getProperty("repoSuffix", "fork1"));

    private static final File DIR_OAK_FILE_STORE = new File(DIR_OAK_REPO_HOME, KEY_FILESTORE);
    private static final File DIR_OAK_BLOB_STORE = new File(DIR_OAK_FILE_STORE, "blobstore");

    private static final String INTERMEDIATE_PATH = "intermediate";

    // TODO: add unique prefix
    AtomicInteger repoId = new AtomicInteger(0);

    @Override
    public RepositoryWithMetadata createRepository(boolean useFileStore, boolean enablePrincipalBasedAuthorization, String... cugEnabledPaths) throws IOException, RepositoryException {
        final Jcr jcr;
        final FileStore fileStore;
        if (useFileStore) {
            BlobStore blobStore = createBlobStore();
            DIR_OAK_FILE_STORE.mkdirs();
            try {
                fileStore = FileStoreBuilder.fileStoreBuilder(DIR_OAK_FILE_STORE)
                        .withBlobStore(blobStore)
                        .build();
            } catch (InvalidFileStoreVersionException e) {
                throw new IOException("Cannot build blob store", e);
            }
            SegmentNodeStore nodeStore = SegmentNodeStoreBuilders.builder(fileStore).build();
            jcr = new Jcr(nodeStore);
        } else {
            // in-memory repo
            jcr = new Jcr();
            fileStore = null;
        }
        Repository repository = jcr
                .with(createSecurityProvider(enablePrincipalBasedAuthorization, cugEnabledPaths))
                .withAtomicCounter()
                .createRepository();

        // setup default read ACL for everyone
        Session admin = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
        AccessControlUtils.addAccessControlEntry(admin, "/", EveryonePrincipal.getInstance(), new String[]{"jcr:read"}, true);
        admin.save();
        admin.logout();
        return new RepositoryWithMetadata(repository, Collections.singletonMap(KEY_FILESTORE, fileStore));
    }

    private static BlobStore createBlobStore() throws IOException {
        DIR_OAK_BLOB_STORE.mkdirs();
        FileDataStore fds = new FileDataStore();
        fds.setMinRecordLength(4092);
        fds.setPath(DIR_OAK_BLOB_STORE.getAbsolutePath());
        fds.init(DIR_OAK_REPO_HOME.getAbsolutePath());
        return new DataStoreBlobStore(fds);
    }

    private static ConfigurationParameters getSecurityConfigurationParameters() {
        Properties userProps = new Properties();
        AuthorizableNodeName nameGenerator = new RandomAuthorizableNodeName();

        userProps.put(UserConstants.PARAM_USER_PATH, "/home/users");
        userProps.put(UserConstants.PARAM_GROUP_PATH, "/home/groups");
        userProps.put(AccessControlAction.USER_PRIVILEGE_NAMES, new String[] {PrivilegeConstants.JCR_ALL});
        userProps.put(AccessControlAction.GROUP_PRIVILEGE_NAMES, new String[] {PrivilegeConstants.JCR_READ});
        userProps.put(ProtectedItemImporter.PARAM_IMPORT_BEHAVIOR, ImportBehavior.NAME_BESTEFFORT);
        userProps.put(UserConstants.PARAM_AUTHORIZABLE_NODE_NAME, nameGenerator);
        userProps.put("cacheExpiration", 3600*1000);
        Properties authzProps = new Properties();
        authzProps.put(ProtectedItemImporter.PARAM_IMPORT_BEHAVIOR, ImportBehavior.NAME_BESTEFFORT);
        return ConfigurationParameters.of(
                UserConfiguration.NAME, ConfigurationParameters.of(userProps),
                AuthorizationConfiguration.NAME, ConfigurationParameters.of(authzProps));
    }

    private SecurityProvider createSecurityProvider(boolean enablePrincipalBasedAuthorization, String... cugEnabledPaths) {
        SecurityProvider securityProvider = SecurityProviderBuilder.newBuilder()
                .with(getSecurityConfigurationParameters())
                .withRootProvider(new RootProviderService())
                .withTreeProvider(new TreeProviderService()).build();
        
        if (enablePrincipalBasedAuthorization) {
            FilterProviderImpl fp = new FilterProviderImpl();
            Map<String, Object> properties = ImmutableMap.of("path", getServiceUserPath());
            FilterProviderImpl.Configuration configuration = Converters.standardConverter().convert(properties).to(FilterProviderImpl.Configuration.class);
            fp.activate(configuration, Collections.emptyMap());
    
            PrincipalBasedAuthorizationConfiguration principalBasedAuthorizationConfiguration = new PrincipalBasedAuthorizationConfiguration();
            principalBasedAuthorizationConfiguration.bindFilterProvider(fp);
            principalBasedAuthorizationConfiguration.bindMountInfoProvider(Mounts.defaultMountInfoProvider());
            SecurityProviderHelper.updateConfig(securityProvider, principalBasedAuthorizationConfiguration, AuthorizationConfiguration.class);
        }
        if (cugEnabledPaths.length > 0) {
            ConfigurationParameters params = ConfigurationParameters.of(
                    "cugSupportedPaths", cugEnabledPaths,
                    "cugEnabled", true
            );
            CugConfiguration cugConfiguration = new CugConfiguration();
            cugConfiguration.setParameters(params);
            SecurityProviderHelper.updateConfig(securityProvider, cugConfiguration, AuthorizationConfiguration.class);
        }
        return securityProvider;
    }

    @Override
    public void closeRepository(RepositoryWithMetadata repositoryWithMetadata) throws IOException {
        ((org.apache.jackrabbit.oak.jcr.repository.RepositoryImpl) repositoryWithMetadata.getRepository()).shutdown();
        final FileStore fileStore = (FileStore) repositoryWithMetadata.getMetadata().get(KEY_FILESTORE);
        if (fileStore != null) {
            fileStore.close();
        }
        RepositoryProviderHelper.deleteDirectory(DIR_OAK_REPO_HOME);
    }

    @Override
    public String getServiceUserPath() {
        String userPath = getSecurityConfigurationParameters().getConfigValue(UserConfiguration.NAME, ConfigurationParameters.EMPTY).getConfigValue(UserConstants.PARAM_USER_PATH, UserConstants.DEFAULT_USER_PATH);
        return PathUtils.concat(userPath, UserConstants.DEFAULT_SYSTEM_RELATIVE_PATH, INTERMEDIATE_PATH);
    }

    @Override
    public boolean isOak() {
        return true;
    }
}
