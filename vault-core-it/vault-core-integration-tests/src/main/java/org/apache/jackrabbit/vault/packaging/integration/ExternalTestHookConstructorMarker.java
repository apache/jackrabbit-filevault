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

import org.apache.jackrabbit.vault.packaging.InstallContext;
import org.apache.jackrabbit.vault.packaging.InstallHook;
import org.apache.jackrabbit.vault.packaging.PackageException;

/**
 * {@code InstallHook} whose <b>constructor</b> records that it was instantiated.
 * Used to verify that an unauthorized (non-admin) install does not load or
 * instantiate hook classes before the admin-session authorization check runs,
 * i.e. that attacker-supplied code in a hook constructor is never executed.
 */
public class ExternalTestHookConstructorMarker implements InstallHook {

    public static volatile boolean constructed = false;

    public static void reset() {
        constructed = false;
    }

    public ExternalTestHookConstructorMarker() {
        constructed = true;
    }

    public void execute(InstallContext context) throws PackageException {
        // no-op; the constructor side-effect is what the test asserts on
    }
}
