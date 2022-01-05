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
package org.apache.jackrabbit.vault.validation;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.jackrabbit.vault.validation.impl.util.ValidatorSettingsImpl;
import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.Validator;
import org.apache.jackrabbit.vault.validation.spi.ValidatorFactory;
import org.apache.jackrabbit.vault.validation.spi.ValidatorSettings;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.xml.sax.SAXException;

@RunWith(MockitoJUnitRunner.class)
public class ValidationExecutorFactoryTest {

    private ValidationExecutorFactory executorFactory;

    @Mock
    private Validator validator1;
    @Mock
    private Validator validator2;
    @Mock
    private Validator validator3;
    @Mock
    private ValidatorFactory validatorFactory1;
    @Mock
    private ValidatorFactory validatorFactory2;
    @Mock
    private ValidatorFactory validatorFactory3;
    @Mock
    private ValidationContext context;
    
    private Map<String, ValidatorSettings> validatorsSettings;
    List<ValidatorFactory> validatorFactories;

    @Before
    public void setUp() {
        validatorFactories = new LinkedList<>();
        Mockito.when(validatorFactory1.getId()).thenReturn("id1");
        Mockito.when(validatorFactory1.getServiceRanking()).thenReturn(3);
        Mockito.when(validatorFactory1.createValidator(Mockito.any(ValidationContext.class), Mockito.any())).thenReturn(validator1);
        validatorFactories.add(validatorFactory1);
        Mockito.when(validatorFactory2.getId()).thenReturn("id2");
        Mockito.when(validatorFactory2.getServiceRanking()).thenReturn(2);
        Mockito.when(validatorFactory2.createValidator(Mockito.any(ValidationContext.class), Mockito.any())).thenReturn(validator2);
        validatorFactories.add(validatorFactory2);
        Mockito.when(validatorFactory3.getId()).thenReturn("id3");
        Mockito.when(validatorFactory3.getServiceRanking()).thenReturn(0);
        Mockito.when(validatorFactory3.createValidator(Mockito.any(ValidationContext.class), Mockito.any())).thenReturn(validator3);
        validatorFactories.add(validatorFactory3);
       
        executorFactory = new ValidationExecutorFactory(validatorFactories);
        validatorsSettings = new HashMap<>();
    }

    @Test
    public void testOrderOfValidators() throws ParserConfigurationException, SAXException {
        // first validate order
        ValidationExecutor executor =  executorFactory.createValidationExecutor(context, true, true, validatorsSettings);
        if (executor == null) {
            throw new IllegalStateException("Could not create validation executor!");
        }
        Map<String, Validator> actualValidators = executor.getAllValidatorsById();
        Map<String, Validator> expectedValidators = new LinkedHashMap<>();
        expectedValidators.put("id1", validator1);
        expectedValidators.put("id2", validator2);
        expectedValidators.put("id3", validator3);
        Assert.assertEquals(expectedValidators.toString(), actualValidators.toString());
        MatcherAssert.assertThat(actualValidators.keySet(), Matchers.contains("id1", "id2", "id3"));
        // the reinstantiate with a different order
        Collections.shuffle(validatorFactories);
        executorFactory = new ValidationExecutorFactory(validatorFactories);
        executor =  executorFactory.createValidationExecutor(context, true, true, validatorsSettings);
        if (executor == null) {
            throw new IllegalStateException("Could not create validation executor!");
        }
        actualValidators = executor.getAllValidatorsById();
        // the returned validators should still have the same order
        Assert.assertEquals(expectedValidators.toString(), actualValidators.toString());
        MatcherAssert.assertThat(actualValidators.keySet(), Matchers.contains("id1", "id2", "id3"));
    }

    @Test
    public void testOrderOfValidatorFactories()  {
        MatcherAssert.assertThat(executorFactory.validatorFactories, Matchers.contains(validatorFactory1, validatorFactory2, validatorFactory3));
        Collections.shuffle(validatorFactories);
        executorFactory = new ValidationExecutorFactory(validatorFactories);
        MatcherAssert.assertThat(executorFactory.validatorFactories, Matchers.contains(validatorFactory1, validatorFactory2, validatorFactory3));
    }

    @Test
    public void testOrderOfValidatorsWithSameRanking() {
        Mockito.when(validatorFactory2.getServiceRanking()).thenReturn(3);
        Mockito.when(validatorFactory3.getServiceRanking()).thenReturn(3);
        executorFactory = new ValidationExecutorFactory(validatorFactories);
        MatcherAssert.assertThat(executorFactory.validatorFactories, Matchers.containsInAnyOrder(validatorFactory1, validatorFactory2, validatorFactory3));
    }

    @Test
    public void testValidatorSettings() throws ParserConfigurationException, SAXException {
        ValidatorSettings settings1 = new ValidatorSettingsImpl("option1", "value1");
        validatorsSettings.put("id1", settings1);
        ValidatorSettings settings2 = new ValidatorSettingsImpl(true);
        validatorsSettings.put("id2", settings2);
        ValidatorSettings settings3 = new ValidatorSettingsImpl(ValidationMessageSeverity.INFO);
        validatorsSettings.put("id3", settings3);
        executorFactory.createValidationExecutor(context, true, true, validatorsSettings);
        Mockito.verify(validatorFactory1).createValidator(context, settings1);
        Mockito.verify(validatorFactory2, Mockito.never()).createValidator(Mockito.any(), Mockito.any());
        Mockito.verify(validatorFactory3).createValidator(context, settings3);
    }
}
