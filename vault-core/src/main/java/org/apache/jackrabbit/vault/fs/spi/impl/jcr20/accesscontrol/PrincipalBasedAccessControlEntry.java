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
package org.apache.jackrabbit.vault.fs.spi.impl.jcr20.accesscontrol;

import javax.jcr.RepositoryException;
import javax.jcr.Value;

import java.util.Collection;
import java.util.Map;

import org.apache.jackrabbit.api.security.authorization.PrincipalAccessControlList;

public class PrincipalBasedAccessControlEntry extends AbstractAccessControlEntry {

    public static class Builder extends AbstractAccessControlEntry.Builder<PrincipalBasedAccessControlEntry> {
        final String effectivePath;

        public Builder(Collection<String> privileges, String effectivePath) {
            super(privileges);
            this.effectivePath = effectivePath;
        }

        @Override
        public PrincipalBasedAccessControlEntry build() {
            return new PrincipalBasedAccessControlEntry(effectivePath, privileges, restrictions);
        }
    }

    final String effectivePath;

    public PrincipalBasedAccessControlEntry(PrincipalAccessControlList.Entry entry) throws RepositoryException {
        super(entry);
        this.effectivePath = entry.getEffectivePath();
    }

    public PrincipalBasedAccessControlEntry(
            String effectivePath, Collection<String> privileges, Map<String, Value[]> restrictions) {
        super(privileges, restrictions);
        this.effectivePath = effectivePath;
    }
}
