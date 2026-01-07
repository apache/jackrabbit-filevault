/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.validation;

import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ValidationViolationTest {

    private List<ValidationMessage> messages;

    private Throwable myThrowable1;
    private Throwable myThrowable2;

    @Before
    public void setUp() {
        myThrowable1 = new Exception("myThrowable1");
        myThrowable2 = new Exception("myThrowable2");
        messages = new LinkedList<>();
        messages.add(new ValidationMessage(ValidationMessageSeverity.DEBUG, "message1"));
        messages.add(new ValidationMessage(ValidationMessageSeverity.INFO, "message2", 1, 2, myThrowable1));
        messages.add(new ValidationViolation(
                "myid",
                ValidationMessageSeverity.ERROR,
                "message3",
                Paths.get("child"),
                null,
                "nodepath1",
                3,
                4,
                myThrowable2));
        messages.add(new ValidationViolation(
                "myid",
                ValidationMessageSeverity.ERROR,
                "message4",
                Paths.get("child"),
                Paths.get("base"),
                "nodepath1",
                3,
                4,
                myThrowable2));
    }

    @Test
    public void testWrapMessagesNoOverwrite() {
        Collection<ValidationViolation> violations =
                ValidationViolation.wrapMessages(null, messages, null, null, null, 0, 0);
        ValidationExecutorTest.assertViolation(
                violations,
                ValidationMessageSeverity.DEBUG,
                new ValidationViolation(ValidationMessageSeverity.DEBUG, "message1"),
                new ValidationViolation(
                        null, ValidationMessageSeverity.INFO, "message2", null, null, null, 1, 2, myThrowable1),
                ValidationViolation.class.cast(messages.get(2)),
                ValidationViolation.class.cast(messages.get(3)));
    }

    @Test
    public void testWrapMessagesFullOverwrite() {
        Collection<ValidationViolation> violations = ValidationViolation.wrapMessages(
                "myid2", messages, Paths.get("child1"), Paths.get("base1"), "nodepath2", 10, 20);
        ValidationExecutorTest.assertViolation(
                violations,
                ValidationMessageSeverity.DEBUG,
                new ValidationViolation(
                        "myid2",
                        ValidationMessageSeverity.DEBUG,
                        "message1",
                        Paths.get("child1"),
                        Paths.get("base1"),
                        "nodepath2",
                        10,
                        20,
                        null),
                new ValidationViolation(
                        "myid2",
                        ValidationMessageSeverity.INFO,
                        "message2",
                        Paths.get("child1"),
                        Paths.get("base1"),
                        "nodepath2",
                        1,
                        2,
                        null),
                new ValidationViolation(
                        "myid",
                        ValidationMessageSeverity.ERROR,
                        "message3",
                        Paths.get("child"),
                        Paths.get("base1"),
                        "nodepath1",
                        3,
                        4,
                        myThrowable2),
                new ValidationViolation(
                        "myid",
                        ValidationMessageSeverity.ERROR,
                        "message4",
                        Paths.get("child"),
                        Paths.get("base"),
                        "nodepath1",
                        3,
                        4,
                        myThrowable2));
    }

    @Test
    public void testValidationViolation() {
        ValidationViolation violation = new ValidationViolation(
                "myid2",
                ValidationMessageSeverity.DEBUG,
                "message1",
                Paths.get("child1"),
                Paths.get("base1"),
                "nodepath2",
                10,
                20,
                null);
        Assert.assertEquals("myid2", violation.getValidatorId());
        Assert.assertEquals(ValidationMessageSeverity.DEBUG, violation.getSeverity());
        Assert.assertEquals("message1", violation.getMessage());
        Assert.assertEquals(Paths.get("base1", "child1"), violation.getAbsoluteFilePath());
        Assert.assertEquals("nodepath2", violation.getNodePath());
        Assert.assertEquals(10, violation.getLine());
        Assert.assertEquals(20, violation.getColumn());
    }
}
