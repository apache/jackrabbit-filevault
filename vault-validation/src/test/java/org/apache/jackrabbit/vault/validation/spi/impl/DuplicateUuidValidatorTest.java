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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Collections;

import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.apache.jackrabbit.vault.validation.AnyValidationViolationMessageMatcher;
import org.apache.jackrabbit.vault.validation.ValidationExecutorTest;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.util.NodeContextImpl;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;

public class DuplicateUuidValidatorTest {

    private DuplicateUuidValidator validator;

    @Before
    public void setUp() throws IOException, ConfigurationException {
        try (InputStream input = this.getClass().getResourceAsStream("/filter.xml"))  {
            DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
            filter.load(input);
            validator = new DuplicateUuidValidator(ValidationMessageSeverity.ERROR, filter);
        }
    }

    @Test
    public void testWithDuplicates() {
        // node with id "1"
        DocViewNode2 node = new DocViewNode2(NameConstants.JCR_ROOT, Collections.singleton(new DocViewProperty2(NameConstants.JCR_UUID, "1")));
        MatcherAssert.assertThat(validator.validate(node, new NodeContextImpl("/apps/test/node1", Paths.get("node1"), Paths.get("")), false), AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        
        // node with id "2"
        node = new DocViewNode2(NameConstants.JCR_ROOT, Collections.singleton(new DocViewProperty2(NameConstants.JCR_UUID, "2")));
        MatcherAssert.assertThat(validator.validate(node, new NodeContextImpl("/apps/test/node2", Paths.get("node2"), Paths.get("")), false), AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        
        // another node with id "1"
        node = new DocViewNode2(NameConstants.JCR_ROOT, Collections.singleton(new DocViewProperty2(NameConstants.JCR_UUID, "1")));
        ValidationExecutorTest.assertViolation(validator.validate(node, new NodeContextImpl("/apps/test/node3", Paths.get("node3"), Paths.get("")), false),
                new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(DuplicateUuidValidator.MESSAGE_DUPLICATE_UUID, "1", "/apps/test/node1", "/apps/test/node3")));

        MatcherAssert.assertThat(validator.done(), AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
    }

    // TODO: check duplicates in different packages
    
}
