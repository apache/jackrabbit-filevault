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

import java.util.Calendar;
import java.util.Properties;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.packaging.InstallContext;
import org.apache.jackrabbit.vault.packaging.InstallHook;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>ExampleHook</code>...
 */
public class ExampleHook implements InstallHook {

    public static String PROP_COPY_FROM = "hook-example-copyfrom";

    public static String PROP_COPY_TO = "hook-example-copyto";

    public static String PROP_TEST_NODE = "hook-example-testnode";

    private String testNodePath;

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(ExampleHook.class);

    public void execute(InstallContext ctx) throws PackageException {
        log.info("Executing Hook in phase {}", ctx.getPhase());
        try {
            switch (ctx.getPhase()) {
                case END:
                    doEnd(ctx);
                    break;
                case INSTALLED:
                    doInstalled(ctx);
                    break;
                case INSTALL_FAILED:
                    doInstallFailed(ctx);
                    break;
                case PREPARE:
                    doPrepare(ctx);
                    break;
                case PREPARE_FAILED:
                    doPrepareFailed(ctx);
                    break;
            }
        } catch (RepositoryException e) {
            throw new PackageException(e);
        }
    }

    private void doPrepare(InstallContext ctx) throws PackageException, RepositoryException {
        // read the properties from the package
        Properties props = ctx.getPackage().getMetaInf().getProperties();
        String copyFrom = props.getProperty(PROP_COPY_FROM, "");
        if (copyFrom.length() == 0) {
            throw new PackageException("hook-example needs " + PROP_COPY_FROM + " property set in properties.xml");
        }
        String copyTo = props.getProperty(PROP_COPY_TO, "");
        if (copyTo.length() == 0) {
            throw new PackageException("hook-example needs " + PROP_COPY_TO + " property set in properties.xml");
        }
        testNodePath = props.getProperty(PROP_TEST_NODE, "");
        if (testNodePath.length() == 0) {
            throw new PackageException("hook-example needs " + PROP_TEST_NODE + " property set in properties.xml");
        }
        copyTo += "_" + System.currentTimeMillis();
        ctx.getSession().getWorkspace().copy(copyFrom, copyTo);
        log.info("hook-example copied {} to {}", copyFrom, copyTo);

    }

    private void doPrepareFailed(InstallContext ctx) {
        // this is invoked when any of the hooks (including our self) threw a
        // package exception during prepare
    }

    private void doEnd(InstallContext ctx) {
        // here we could clean up any allocated resources
    }

    private void doInstalled(InstallContext ctx) throws RepositoryException {
        // update a property in the install
        Node testNode = ctx.getSession().getNode(testNodePath);
        testNode.setProperty("hook-example", Calendar.getInstance());
        testNode.save();
    }

    private void doInstallFailed(InstallContext ctx) {
        // this is called when installation failed
    }


}