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

package org.apache.jackrabbit.vault.packaging.integration;

import java.io.IOException;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.junit.Test;


public class MandatoryNode extends IntegrationTestBase {
 
    @Test
    public void testSimpleFileAggregateOverwritesChildNodes()
            throws RepositoryException, IOException, PackageException {

        extractVaultPackage("/test-packages/wcm-rollout-config-1.zip");

        // test if nodes exist
        assertNodeExists("/libs/msm/wcm/rolloutconfigs/default/contentUpdate");
        assertNodeExists("/libs/msm/wcm/rolloutconfigs/default/contentCopy");
        assertNodeExists("/libs/msm/wcm/rolloutconfigs/default/contentDelete");
        assertNodeExists("/libs/msm/wcm/rolloutconfigs/default/referencesUpdate");
        assertNodeExists("/libs/msm/wcm/rolloutconfigs/default/productUpdate");
        assertNodeExists("/libs/msm/wcm/rolloutconfigs/default/orderChildren");
        
        extractVaultPackage("/test-packages/wcm-rollout-config-2.zip");

        assertNodeMissing("/libs/msm/wcm/rolloutconfigs/default/contentUpdate");
        assertNodeMissing("/libs/msm/wcm/rolloutconfigs/default/contentDelete");
        assertNodeMissing("/libs/msm/wcm/rolloutconfigs/default/referencesUpdate");
        assertNodeMissing("/libs/msm/wcm/rolloutconfigs/default/productUpdate");
        assertNodeMissing("/libs/msm/wcm/rolloutconfigs/default/orderChildren");
        
        assertNodeExists("/libs/msm/wcm/rolloutconfigs/default/activate");
        assertNodeExists("/libs/msm/wcm/rolloutconfigs/default/contentCopy");
    }
}
