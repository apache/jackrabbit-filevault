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

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageInfo;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.validation.spi.PropertiesValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;

public final class DependencyValidator implements PropertiesValidator {

    static final String MESSAGE_DEPENDENCIES_WITH_OVERLAPPING_FILTERS = "Dependency '%s' defines same filter root '%s' as dependency '%s'";
    static final String MESSAGE_UNRESOLVED_DEPENDENCY = "Dependency '%s'was not successfully resolved and can therefore not be used for analysis.";
    private final Collection<PackageInfo> dependenciesMetaInfo;
    private final ValidationMessageSeverity severity;
    private final ValidationMessageSeverity severityForUnresolvedDependencies;
    
    public DependencyValidator(ValidationMessageSeverity severity, ValidationMessageSeverity severityForUnresolvedDependencies, Collection<PackageInfo> dependenciesMetaInfo) {
        this.dependenciesMetaInfo = dependenciesMetaInfo;
        this.severity = severity;
        this.severityForUnresolvedDependencies = severityForUnresolvedDependencies;
    }

    @Override
    public Collection<ValidationMessage> done() {
        return null;
    }

    @Override
    public Collection<ValidationMessage> validate(PackageProperties properties) {
        
        // use resolved dependencies
        Collection<ValidationMessage> messages = new LinkedList<>();
        
        Map<String, PackageInfo> roots = new HashMap<>();
        
        // check for unresolved dependencies!
        for (Dependency dependency : properties.getDependencies()) {
            boolean isDependencyResolved = false;
            for (PackageInfo resolvedDependency : dependenciesMetaInfo) {
                if (dependency.matches(resolvedDependency.getId())) {
                    for (PathFilterSet set : resolvedDependency.getFilter().getFilterSets()) {
                        String root = set.getRoot();
                        PackageInfo existing = roots.get(root);
                        if (existing != null) {
                            String msg = String.format(MESSAGE_DEPENDENCIES_WITH_OVERLAPPING_FILTERS,
                                    resolvedDependency.getId(), root, existing.getId());
                            messages.add(new ValidationMessage(severity, msg));
                        }
                        roots.put(root, resolvedDependency);
                    }
                    isDependencyResolved = true;
                    break;
                }
            }
            if (!isDependencyResolved) {
                String msg = String.format(MESSAGE_UNRESOLVED_DEPENDENCY, dependency);
                messages.add(new ValidationMessage(severityForUnresolvedDependencies, msg));
                continue;
            }
            // TODO: check for overlapping roots with current filter.xml
        }
        return messages;
    }

}
