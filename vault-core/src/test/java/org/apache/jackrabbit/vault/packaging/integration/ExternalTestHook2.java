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

package org.apache.jackrabbit.vault.packaging.integration;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.packaging.InstallContext;
import org.apache.jackrabbit.vault.packaging.InstallHook;
import org.apache.jackrabbit.vault.packaging.PackageException;

/**
 * <code>ExternalTestHook</code> that is called by the external_hook package.
 */
public class ExternalTestHook2 implements InstallHook {

    public void execute(InstallContext context) throws PackageException {
        try {
            context.getSession().getNode("/testroot").setProperty("TestHook2", context.getPhase().toString());
            context.getSession().save();
        } catch (RepositoryException e) {
            throw new PackageException(e);
        }
    }
}