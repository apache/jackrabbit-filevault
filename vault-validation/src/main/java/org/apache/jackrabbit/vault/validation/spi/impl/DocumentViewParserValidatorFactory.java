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

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.jackrabbit.vault.validation.spi.Validator;
import org.apache.jackrabbit.vault.validation.spi.ValidatorFactory;
import org.apache.jackrabbit.vault.validation.spi.ValidatorSettings;
import org.kohsuke.MetaInfServices;
import org.xml.sax.SAXException;

@MetaInfServices
public class DocumentViewParserValidatorFactory implements ValidatorFactory {

    private SAXParser saxParser;

    public DocumentViewParserValidatorFactory() throws ParserConfigurationException, SAXException {

        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        saxParser = spf.newSAXParser();
    }
    @Override
    public Validator createValidator(ValidationContext context, ValidatorSettings settings) {
        return new DocumentViewParserValidator(saxParser, settings.getDefaultSeverity());
    }

    @Override
    public boolean shouldValidateSubpackages() {
        return false;
    }

    @Override
    public String getId() {
        return ValidatorFactory.ID_PREFIX_JACKRABBIT + "docviewparser";
    }

    @Override
    public int getServiceRanking() {
        return Integer.MAX_VALUE;
    }

    
}
