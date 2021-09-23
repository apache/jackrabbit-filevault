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

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.apache.jackrabbit.vault.validation.AnyValidationMessageMatcher;
import org.apache.jackrabbit.vault.validation.ValidationExecutorTest;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.util.NodeContextImpl;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

public class AccessControlValidatorTest {

    private AccessControlValidator validator;

    @Test
    public void testWithACLsAndMerge() {
        validator = new AccessControlValidator(false, ValidationMessageSeverity.ERROR, AccessControlHandling.MERGE);
        
        DocViewNode2 node = new DocViewNode2(NameConstants.JCR_CONTENT, Arrays.asList(
        		new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "rep:ACL"),
        		new DocViewProperty2(NameConstants.JCR_TITLE, "title")));
        Collection<ValidationMessage> messages = validator.validate(node,  new NodeContextImpl("/apps/test/deep", Paths.get(".content.xml"), Paths.get("base")), false);
        MatcherAssert.assertThat(messages, AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.done(), AnyValidationMessageMatcher.noValidationInCollection());
    }

    @Test
    public void testWithoutACLsAndClear() {
        validator = new AccessControlValidator(false, ValidationMessageSeverity.ERROR, AccessControlHandling.CLEAR);
        
        DocViewNode2 node = new DocViewNode2(NameConstants.JCR_CONTENT, Arrays.asList(
        		new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "nt:unstructured"),
        		new DocViewProperty2(NameConstants.JCR_TITLE, "title")));
        Collection<ValidationMessage> messages = validator.validate(node,  new NodeContextImpl("/apps/test/deep", Paths.get(".content.xml"), Paths.get("base")), false);
        MatcherAssert.assertThat(messages, AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.done(), AnyValidationMessageMatcher.noValidationInCollection());
    }

    @Test
    public void testWithoutACLsAndMerge() {
        validator = new AccessControlValidator(false, ValidationMessageSeverity.ERROR, AccessControlHandling.MERGE);
        
        DocViewNode2 node = new DocViewNode2(NameConstants.JCR_CONTENT, Arrays.asList(
        		new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "nt:unstructured"),
        		new DocViewProperty2(NameConstants.JCR_TITLE, "title")));
        Collection<ValidationMessage> messages = validator.validate(node,  new NodeContextImpl("/apps/test/deep", Paths.get(".content.xml"), Paths.get("base")), false);
        MatcherAssert.assertThat(messages, AnyValidationMessageMatcher.noValidationInCollection());
        ValidationExecutorTest.assertViolation(validator.done(), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(AccessControlValidator.MESSAGE_INEFFECTIVE_ACCESS_CONTROL_LIST, AccessControlHandling.MERGE)));
        
        // the same test in incremental runs
        validator = new AccessControlValidator(true, ValidationMessageSeverity.ERROR, AccessControlHandling.MERGE);
        MatcherAssert.assertThat(validator.done(), AnyValidationMessageMatcher.noValidationInCollection());
    }

    @Test
    public void testWithACLsAndClear() {
        validator = new AccessControlValidator(false, ValidationMessageSeverity.ERROR, AccessControlHandling.CLEAR);
        
        DocViewNode2 node = new DocViewNode2(NameConstants.JCR_CONTENT, Arrays.asList(
        		new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "rep:PrincipalPolicy"),
        		new DocViewProperty2(NameConstants.JCR_TITLE, "title")));
        Collection<ValidationMessage> messages = validator.validate(node, new NodeContextImpl("/apps/test/deep", Paths.get(".content.xml"), Paths.get("base")), false);
        
        ValidationExecutorTest.assertViolation(messages,
                new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(AccessControlValidator.MESSAGE_IGNORED_ACCESS_CONTROL_LIST, AccessControlHandling.CLEAR)));
        MatcherAssert.assertThat(validator.done(), AnyValidationMessageMatcher.noValidationInCollection());
    }

    @Test
    public void testWithACLsAndIgnore() {
        validator = new AccessControlValidator(false, ValidationMessageSeverity.ERROR, AccessControlHandling.IGNORE);
        
        DocViewNode2 node = new DocViewNode2(NameConstants.JCR_CONTENT, Arrays.asList(
        		new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "rep:PrincipalPolicy"),
        		new DocViewProperty2(NameConstants.JCR_TITLE, "title")));
        Collection<ValidationMessage> messages = validator.validate(node, new NodeContextImpl("/apps/test/deep", Paths.get(".content.xml"), Paths.get("base")), false);
        
        ValidationExecutorTest.assertViolation(messages,
                new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(AccessControlValidator.MESSAGE_IGNORED_ACCESS_CONTROL_LIST, AccessControlHandling.IGNORE)));
        MatcherAssert.assertThat(validator.done(), AnyValidationMessageMatcher.noValidationInCollection());
    }
}
