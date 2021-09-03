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
package org.apache.jackrabbit.vault.validation.spi.impl;

import org.junit.Assert;
import org.junit.Test;

public class PackageTypeValidatorFactoryTest {

    @Test
    public void testDefaultJcrInstallerNodePathRegex() {
        Assert.assertTrue(PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX.matcher("/apps/test/install/mypackage.jar").matches());
        Assert.assertTrue(PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX.matcher("/apps/child/grandchild/grandgrandchild/install.runmode1.runmode2/30/mypackage.zip").matches());
        Assert.assertTrue(PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX.matcher("/apps/test/install/15/mypackage.jar").matches());
        Assert.assertTrue(PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX.matcher("/apps/test/install/bla.xml").matches()); // assume sling:OsgiConfig  property
        Assert.assertFalse(PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX.matcher("/apps/test/installother").matches());
    }

    @Test
    public void testDefaultJcrInstallerAdditionalNodePathRegex() {
        Assert.assertFalse(PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_ADITIONAL_FILE_NODE_PATH_REGEX.matcher("/apps/test/install/myconfig.xml").matches());
        Assert.assertFalse(PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_ADITIONAL_FILE_NODE_PATH_REGEX.matcher("/apps/test/install/.content.xml").matches());
        Assert.assertTrue(PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_ADITIONAL_FILE_NODE_PATH_REGEX.matcher("/apps/test/install/15/mypackage.jar").matches());
    }
}
