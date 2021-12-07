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
package org.apache.jackrabbit.vault.validation.it;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;

import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.validation.ValidationViolation;
import org.apache.jackrabbit.vault.validation.impl.util.ValidatorSettingsImpl;
import org.apache.jackrabbit.vault.validation.spi.impl.nodetype.NodeTypeValidatorFactory;
import org.hamcrest.MatcherAssert;
import org.hamcrest.collection.IsEmptyCollection;
import org.junit.Test;

public class ValidationIT extends AbstractValidationIT {

    @Test
    public void testApplicationPackageWithNoValidationIssues() throws URISyntaxException, IOException, ConfigurationException {
        MatcherAssert.assertThat(validatePackageFolder("/valid-packages/application-package"), new IsEmptyCollection<ValidationViolation>());
    }

    @Test
    public void testContainerPackageWithNoValidationIssues() throws URISyntaxException, IOException, ConfigurationException {
        ValidatorSettingsImpl nodetypeValidatorSettings = new ValidatorSettingsImpl(NodeTypeValidatorFactory.OPTION_CNDS, "tccl:valid-packages/container-package/META-INF/vault/nodetypes.cnd");
        MatcherAssert.assertThat(validatePackageFolder("/valid-packages/container-package", Collections.singletonMap("jackrabbit-nodetypes", nodetypeValidatorSettings)), new IsEmptyCollection<ValidationViolation>());
    }
}
