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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.validation.ValidationExecutorFactory;
import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.Validator;
import org.apache.jackrabbit.vault.validation.spi.ValidatorFactory;
import org.apache.jackrabbit.vault.validation.spi.ValidatorSettings;
import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    static final Collection<String> DEFAULT_VALID_ROOTS = new LinkedList<>(Arrays.asList("/","/libs","/apps","/etc","/var","/tmp","/content"));

    /**
     * the default logger
     */
    private static final Logger log = LoggerFactory.getLogger(ValidationExecutorFactory.class);

    @Override
    public Validator createValidator(ValidationContext context, ValidatorSettings settings) {
        final ValidationMessageSeverity severityForUncoveredAncestorNode;
        if (settings.getOptions().containsKey(OPTION_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES)) {
            String optionValue = settings.getOptions().get(OPTION_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES);
            severityForUncoveredAncestorNode = ValidationMessageSeverity.valueOf(optionValue.toUpperCase());
        } else {
            severityForUncoveredAncestorNode = DEFAULT_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES;
        }
        // severity for ancestor of filter rules
        final ValidationMessageSeverity severityForUncoveredFilterRootAncestors;
        if (PackageType.APPLICATION.equals(context.getProperties().getPackageType())) {
            log.debug("Due to package type 'application' emit error for every uncovered filter root ancestor");
            severityForUncoveredFilterRootAncestors = ValidationMessageSeverity.ERROR;
            if (settings.getOptions().containsKey(OPTION_SEVERITY_FOR_UNCOVERED_FILTER_ROOT_ANCESTORS)) {
                log.warn("Disregard option '{}' as package type is application which sets this violation to severity 'error'", OPTION_SEVERITY_FOR_UNCOVERED_FILTER_ROOT_ANCESTORS);
            }
        } else {
            if (settings.getOptions().containsKey(OPTION_SEVERITY_FOR_UNCOVERED_FILTER_ROOT_ANCESTORS)) {
                String optionValue = settings.getOptions().get(OPTION_SEVERITY_FOR_UNCOVERED_FILTER_ROOT_ANCESTORS);
                severityForUncoveredFilterRootAncestors = ValidationMessageSeverity.valueOf(optionValue.toUpperCase());
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
            validRoots.addAll(Arrays.asList(optionValue.split(",")));
        } else {
            validRoots.addAll(DEFAULT_VALID_ROOTS);
        }
        return new AdvancedFilterValidator(settings.getDefaultSeverity(), severityForUncoveredAncestorNode, severityForUncoveredFilterRootAncestors, severityForOrphanedFilterRules, context.getDependenciesMetaInfo(), context.getFilter(), validRoots);
    }

    @Override
    public boolean shouldValidateSubpackages() {
        return false;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int getServiceRanking() {
        return Integer.MAX_VALUE;
    }

}
