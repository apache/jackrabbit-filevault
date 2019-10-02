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
package org.apache.jackrabbit.filevault.validation.spi.impl;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.apache.jackrabbit.filevault.validation.spi.ValidationContext;
import org.apache.jackrabbit.filevault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.filevault.validation.spi.Validator;
import org.apache.jackrabbit.filevault.validation.spi.ValidatorFactory;
import org.apache.jackrabbit.filevault.validation.spi.ValidatorSettings;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public final class AdvancedFilterValidatorFactory implements ValidatorFactory {

    protected static final String OPTION_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES = "severityForUncoveredAncestorNodes";
    protected static final String OPTION_SEVERITY_FOR_ORPHANED_FILTER_RULES = "severityForOrphanedFilterRules";
    // should take comma-separated list of valid root paths
    protected static final String OPTION_VALID_ROOTS = "validRoots";
    
    protected static final ValidationMessageSeverity DEFAULT_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES = ValidationMessageSeverity.INFO;
    protected static final ValidationMessageSeverity DEFAULT_SEVERITY_FOR_ORPHANED_FILTER_RULES = ValidationMessageSeverity.INFO;
    protected static final Collection<String> DEFAULT_VALID_ROOTS = new LinkedList<>(Arrays.asList("/","/libs","/apps","/etc","/var","/tmp","/content"));

    @Override
    public Validator createValidator(ValidationContext context, ValidatorSettings settings) {
        final ValidationMessageSeverity messageSeverityForUncoveredAncestorNode;
        if (PackageType.APPLICATION.equals(context.getProperties().getPackageType())) {
            messageSeverityForUncoveredAncestorNode = ValidationMessageSeverity.ERROR;
        } else {
            if (settings.getOptions().containsKey(OPTION_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES)) {
                String optionValue = settings.getOptions().get(OPTION_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES);
                messageSeverityForUncoveredAncestorNode = ValidationMessageSeverity.valueOf(optionValue.toUpperCase());
            } else {
                messageSeverityForUncoveredAncestorNode = DEFAULT_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES;
            }
        }
        final ValidationMessageSeverity messageSeverityForOrphanedFilterRules;
        if (settings.getOptions().containsKey(OPTION_SEVERITY_FOR_ORPHANED_FILTER_RULES)) {
            String optionValue = settings.getOptions().get(OPTION_SEVERITY_FOR_ORPHANED_FILTER_RULES);
            messageSeverityForOrphanedFilterRules = ValidationMessageSeverity.valueOf(optionValue.toUpperCase());
        } else {
            messageSeverityForOrphanedFilterRules = DEFAULT_SEVERITY_FOR_ORPHANED_FILTER_RULES;
        }
        Set<String> validRoots = new HashSet<>();
        validRoots.add("");
        if (settings.getOptions().containsKey(OPTION_VALID_ROOTS)) {
            String optionValue = settings.getOptions().get(OPTION_VALID_ROOTS);
            validRoots.addAll(Arrays.asList(optionValue.split(",")));
        } else {
            validRoots.addAll(DEFAULT_VALID_ROOTS);
        }
        
        return new AdvancedFilterValidator(settings.getDefaultSeverity(), messageSeverityForUncoveredAncestorNode, messageSeverityForOrphanedFilterRules, context.getDependenciesMetaInfo(), context.getFilter(), validRoots);
    }

    @Override
    public boolean shouldValidateSubpackages() {
        return false;
    }

    @Override
    public String getId() {
        return ValidatorFactory.PREFIX_JACKRABBIT + "filter";
    }

    @Override
    public int getServiceRanking() {
        return Integer.MAX_VALUE;
    }

}
