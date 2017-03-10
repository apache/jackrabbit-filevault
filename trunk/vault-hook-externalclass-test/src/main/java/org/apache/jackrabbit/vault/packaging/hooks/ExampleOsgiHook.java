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
package org.apache.jackrabbit.vault.packaging.hooks;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.packaging.InstallContext;
import org.apache.jackrabbit.vault.packaging.InstallHook;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.sling.api.resource.ResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example hook that uses a class provided by the webapp (org.apache.sling.api.resource.ResourceUtil)
 * and/or osgi and is not explicitly imported by the vault bundle
 */
public class ExampleOsgiHook implements InstallHook {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(ExampleOsgiHook.class);

    public void execute(InstallContext ctx) throws PackageException {
        String name = ResourceUtil.getName("/foo/test");
        log.info("Executing Hook in phase {}. Testing Resource Util: {}", ctx.getPhase(), name);
        if (ctx.getOptions().getListener() != null) {
            ctx.getOptions().getListener().onMessage(ProgressTrackerListener.Mode.TEXT, "H", "OSGi Hook Test - " + name);
        }
        if (ctx.getPhase() == InstallContext.Phase.INSTALLED) {
            try {
                ctx.getSession().getNode("/testroot").setProperty("TestHook", ctx.getPhase().toString());
                ctx.getSession().save();
            } catch (RepositoryException e) {
                throw new PackageException(e);
            }
        }
    }

}