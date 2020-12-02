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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.Validator;
import org.apache.jackrabbit.vault.validation.spi.ValidatorFactory;
import org.apache.jackrabbit.vault.validation.spi.ValidatorSettings;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

@MetaInfServices
public final class AdvancedFilterValidatorFactory implements ValidatorFactory {

    public static final String ID = ID_PREFIX_JACKRABBIT + "filter";

    public static final String OPTION_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES = "severityForUncoveredAncestorNodes";
    private static final Object OPTION_SEVERITY_FOR_UNCOVERED_FILTER_ROOT_ANCESTORS = "severityForUncoveredFilterRootAncestors";
    public static final String OPTION_SEVERITY_FOR_ORPHANED_FILTER_RULES = "severityForOrphanedFilterRules";
    // should take comma-separated list of valid root paths
    public static final String OPTION_VALID_ROOTS = "validRoots";
    
    static final ValidationMessageSeverity DEFAULT_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES = ValidationMessageSeverity.INFO;
    private static final ValidationMessageSeverity DEFAULT_SEVERITY_FOR_UNCOVERED_FILTER_ROOT_ANCESTORS = ValidationMessageSeverity.WARN;
    static final ValidationMessageSeverity DEFAULT_SEVERITY_FOR_ORPHANED_FILTER_RULES = ValidationMessageSeverity.INFO;
    static final Collection<String> DEFAULT_VALID_ROOTS = new LinkedList<>(Arrays.asList("/","/libs","/apps","/etc","/var","/tmp","/content","/etc/packages"));

    @NotNull private final DocumentBuilderFactory factory;
    /**
     * the default logger
     */
    private static final Logger log = LoggerFactory.getLogger(AdvancedFilterValidatorFactory.class);

    public AdvancedFilterValidatorFactory() throws IOException {
        factory = createFilterXsdAwareDocumentBuilder(null);
    }

    static @NotNull DocumentBuilderFactory createFilterXsdAwareDocumentBuilder(Locale locale) throws IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try (InputStream xsdInput = AdvancedFilterValidatorFactory.class.getResourceAsStream("/filter.xsd")) {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            // load a WXS schema, represented by a Schema instance
            Source schemaFile = new StreamSource(xsdInput);
            Schema schema = schemaFactory.newSchema(schemaFile);
            factory.setSchema(schema);
            if (xsdInput == null) {
                throw new IllegalStateException("Can not load filter.xsd");
            }
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            // optionally adjust locale, https://stackoverflow.com/a/18745978
            if (locale != null) {
                factory.setAttribute("http://apache.org/xml/properties/locale", locale);
            }
        } catch (SAXException e) {
            throw new IOException("Could not parse input as xml", e);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Could not instantiate DOM parser", e);
        }
        return factory;
    }
    
    @Override
    public Validator createValidator(@NotNull ValidationContext context, @NotNull ValidatorSettings settings) {
        final ValidationMessageSeverity severityForUncoveredAncestorNode;
        if (settings.getOptions().containsKey(OPTION_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES)) {
            String optionValue = settings.getOptions().get(OPTION_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES);
            severityForUncoveredAncestorNode = ValidationMessageSeverity.valueOf(optionValue.toUpperCase());
        } else {
            severityForUncoveredAncestorNode = DEFAULT_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES;
        }
        // severity for ancestor of filter rules
        final ValidationMessageSeverity severityForUncoveredFilterRootAncestors;
        if (settings.getOptions().containsKey(OPTION_SEVERITY_FOR_UNCOVERED_FILTER_ROOT_ANCESTORS)) {
            String optionValue = settings.getOptions().get(OPTION_SEVERITY_FOR_UNCOVERED_FILTER_ROOT_ANCESTORS);
            severityForUncoveredFilterRootAncestors = ValidationMessageSeverity.valueOf(optionValue.toUpperCase());
        } else {
            if (PackageType.APPLICATION.equals(context.getProperties().getPackageType())) {
                log.debug("Due to package type 'application' emit error for every uncovered filter root ancestor");
                severityForUncoveredFilterRootAncestors = ValidationMessageSeverity.ERROR;
            } else {
                severityForUncoveredFilterRootAncestors = DEFAULT_SEVERITY_FOR_UNCOVERED_FILTER_ROOT_ANCESTORS;
            }
        }
        
        final ValidationMessageSeverity severityForOrphanedFilterRules;
        if (settings.getOptions().containsKey(OPTION_SEVERITY_FOR_ORPHANED_FILTER_RULES)) {
            String optionValue = settings.getOptions().get(OPTION_SEVERITY_FOR_ORPHANED_FILTER_RULES);
            severityForOrphanedFilterRules = ValidationMessageSeverity.valueOf(optionValue.toUpperCase());
        } else {
            severityForOrphanedFilterRules = DEFAULT_SEVERITY_FOR_ORPHANED_FILTER_RULES;
        }
        Set<String> validRoots = new HashSet<>();
        validRoots.add("");
        if (settings.getOptions().containsKey(OPTION_VALID_ROOTS)) {
            String optionValue = settings.getOptions().get(OPTION_VALID_ROOTS);
            validRoots.addAll(Arrays.asList(optionValue.split("\\s*,\\s*")));
        } else {
            validRoots.addAll(DEFAULT_VALID_ROOTS);
        }
        return new AdvancedFilterValidator(factory, settings.getDefaultSeverity(), severityForUncoveredAncestorNode, severityForUncoveredFilterRootAncestors, severityForOrphanedFilterRules, context.getContainerValidationContext() != null, context.getDependenciesPackageInfo(), context.getFilter(), validRoots);
    }

    @Override
    public boolean shouldValidateSubpackages() {
        // necessary to call nested validators which should be called for subpackages
        return true;
    }

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public int getServiceRanking() {
        return Integer.MAX_VALUE;
    }

}
