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
package org.apache.jackrabbit.vault.packaging.impl;


import javax.annotation.Nonnull;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.events.PackageEvent;
import org.apache.jackrabbit.vault.packaging.events.PackageEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener that receive packaging events and logs them to the activity log.
 */
@Component(immediate = true)
@Service(value = PackageEventListener.class)
public class ActivityLog implements PackageEventListener {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(ActivityLog.class);

    @Activate
    private void activate() {
        log.info("Package Activity Log Started");
    }

    @Override
    public void onPackageEvent(@Nonnull PackageEvent event) {
        String msg;
        if (event.getRelatedIds() != null) {
            msg = String.format("%s: %s (%s)", event.getId(), event.getType(), PackageId.toString(event.getRelatedIds()));
        } else {
            msg = String.format("%s: %s", event.getId(), event.getType());
        }

        if (log.isDebugEnabled()) {
            msg += "\nThe event was triggered here:";
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (int i=1; i<stackTrace.length; i++) {
                StackTraceElement s = stackTrace[i];
                msg += String.format("\n\tat %s.%s(%s:%d)", s.getClassName(), s.getMethodName(), s.getFileName(), s.getLineNumber());
            }
        }
        log.info("{}", msg);
    }
}