/*************************************************************************
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
 ************************************************************************/
package org.apache.jackrabbit.vault.fs.spi.impl.jcr20.accesscontrol;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.vault.util.UncheckedRepositoryException;

public class AbstractAccessControlEntry {
    final Collection<String> privileges;
    final Map<String, Value[]> restrictions;

    protected abstract static class Builder<T extends AbstractAccessControlEntry> implements JackrabbitAccessControlEntryBuilder<T> {
        final Collection<String> privileges;
        final Map<String, Value[]> restrictions;

        Builder(Collection<String> privileges) {
            this.privileges = new HashSet<>(privileges);
            this.restrictions = new HashMap<>();
        }

        @Override
        public void addRestriction(String restrictionName, Value[] values) {
            restrictions.put(restrictionName, values);
        }
    }

    protected AbstractAccessControlEntry(JackrabbitAccessControlEntry entry) throws RepositoryException {
        this(Arrays.stream(entry.getPrivileges()).map(Privilege::getName).collect(Collectors.toList()), 
             Arrays.stream(entry.getRestrictionNames())
                .collect(Collectors.<String, String, Value[]>toMap(rn -> rn, rn -> {
                    try {
                        return entry.getRestrictions(rn);
                    } catch (RepositoryException e) {
                        throw new UncheckedRepositoryException(e);
                    }
                })));
    }

    Map.Entry<Map<String, Value>, Map<String, Value[]>> separateRestrictions(JackrabbitAccessControlList list) throws RepositoryException {
        Map<String, Value> svRestrictions = new HashMap<>();
        Map<String, Value[]> mvRestrictions = new HashMap<>();
        try {
            Map<Boolean, List<String>> restrictionNamesMap = restrictions.keySet().stream().collect(Collectors.partitioningBy(r -> {
                try {
                    return list.isMultiValueRestriction(r);
                } catch (RepositoryException e) {
                    throw new UncheckedRepositoryException(e);
                }
            }));
            restrictionNamesMap.get(Boolean.TRUE).stream().forEach(restrictionName -> mvRestrictions.put(restrictionName, restrictions.get(restrictionName)));
            restrictionNamesMap.get(Boolean.FALSE).stream().forEach(restrictionName -> svRestrictions.put(restrictionName, restrictions.get(restrictionName)[0]));
        } catch (UncheckedRepositoryException e) {
            throw e.getCause();
        }
        return new AbstractMap.SimpleEntry<>(svRestrictions, mvRestrictions);
    }

    protected AbstractAccessControlEntry(Collection<String> privileges, Map<String, Value[]> restrictions) {
        this.privileges = new HashSet<>(privileges);
        this.restrictions = new HashMap<>(restrictions);
    }

    Privilege[] getPrivileges(AccessControlManager acMgr) throws RepositoryException {
        return AccessControlUtils.privilegesFromNames(acMgr, privileges.toArray(new String[0]));
    }
}
