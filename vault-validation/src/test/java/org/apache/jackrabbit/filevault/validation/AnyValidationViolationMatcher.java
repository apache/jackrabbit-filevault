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
package org.apache.jackrabbit.filevault.validation;

import org.apache.jackrabbit.filevault.validation.spi.ValidationMessageSeverity;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;

public class AnyValidationViolationMatcher extends TypeSafeMatcher<ValidationViolation> {

    @Override
    public void describeTo(Description description) {
        description.appendText("ValidationMessage with severity ERROR or WARN");
    }

    @Override
    protected boolean matchesSafely(ValidationViolation item) {
        if (item.getSeverity().ordinal() >= ValidationMessageSeverity.WARN.ordinal()) {
            return true;
        }
        return false;
    }
    
    public static Matcher<Iterable<? super ValidationViolation>> noValidationInCollection() {
        return Matchers.not(Matchers.hasItem(new AnyValidationViolationMatcher()));
    }
}
