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

package org.apache.jackrabbit.vault.fs.io;

import java.util.HashSet;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.spi.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>AutoSave</code>...
 */
public class AutoSave {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(AutoSave.class);

    /**
     * number of modified nodes
     */
    private int numModified;

    /**
     * number of modified nodes at last save
     */
    private int lastSave;

    /**
     * number of modified nodes that trigger a save. default is 1024
     */
    private int threshold = 1024;

    /**
     * set that records the missing mandatory items. save has to be delay until they are resolved
     */
    private final Set<String> missingMandatory = new HashSet<String>();

    /**
     * tracker to use to report progress messages
     */
    private ProgressTracker tracker;

    /**
     * flag controlling if autosave should until simulate
     */
    private boolean dryRun;

    /**
     * debug setting that allows to simulate autosave failures. 
     */
    private int debugFailEach;

    /**
     * debug counter used to trigger failures
     */
    private int debugSaveCount;

    public AutoSave() {
    }

    public AutoSave(int threshold) {
        this.threshold = threshold;
    }

    public AutoSave copy() {
        AutoSave ret = new AutoSave();
        ret.threshold = threshold;
        ret.numModified = numModified;
        ret.lastSave = lastSave;
        ret.tracker = tracker;
        ret.dryRun = dryRun;
        ret.missingMandatory.addAll(missingMandatory);
        ret.debugFailEach = debugFailEach;
        // don't copy save count, otherwise it will fail for ever.
        // ret.debugSaveCount = debugSaveCount;
        return ret;
    }

    public void setTracker(ProgressTracker tracker) {
        this.tracker = tracker;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * Debug settings to allows to produce failures after each <code>debugFailEach</code> save.
     * @param debugFailEach cardinal indicating when to fail 
     */
    public void setDebugFailEach(int debugFailEach) {
        this.debugFailEach = debugFailEach;
    }

    /**
     * Returns <code>true</code> if more than {@link #getThreshold()} nodes are
     * modified.
     * @return <code>true</code> if threshold reached.
     */
    public boolean needsSave() {
        boolean res = (numModified - lastSave) >= threshold;
        if (res && !missingMandatory.isEmpty()) {
            log.info("Threshold of {} reached but still unresolved mandatory items.", threshold);
            res = false;
        }
        return res;
    }

    /**
     * saves the changes under the given node and resets the counter
     * @param session the session to save. can be <code>null</code>
     * @throws RepositoryException if an error occurs.
     */
    public void save(Session session) throws RepositoryException {
        if (threshold == Integer.MAX_VALUE) {
            log.debug("Save disabled.");
            return;
        }
        int diff = numModified - lastSave;
        log.info("Threshold of {} reached. {} approx {} transient changes. {} unresolved", new Object[]{
                threshold,
                dryRun ? "dry run, reverting" : "saving",
                diff,
                missingMandatory.size()
        });
        if (tracker != null) {
            if (dryRun) {
                tracker.track("reverting approx " + diff + " nodes... (dry run)", "");
            } else {
                tracker.track("saving approx " + diff + " nodes...", "");
            }
        }
        if (session != null) {
            if (debugFailEach > 0 && debugSaveCount > 0 && debugSaveCount%debugFailEach == 0) {
                String msg = String.format("Debugging provoked failure after %s saves.", debugSaveCount);
                log.error(msg);
                throw new RepositoryException(msg);
            }

            if (dryRun) {
                session.refresh(false);
            } else {
                try {
                    session.save();
                    debugSaveCount++;
                } catch (RepositoryException e) {
                    log.error("error during auto save - retrying after refresh...");
                    session.refresh(true);
                    session.save();
                    debugSaveCount++;
                }
            }
        }
        lastSave = numModified;
    }

    /**
     * Returns the threshold
     * @return the threshold
     */
    public int getThreshold() {
        return threshold;
    }

    /**
     * Sets the threshold
     * @param threshold the threshold
     */
    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    /**
     * Adds <code>num</code> modified
     * @param num number of modified
     * @return <code>true</code> if threshold is reached
     */
    public boolean modified(int num) {
        numModified+= num;
        return needsSave();
    }

    public void markMissing(String path) {
        missingMandatory.add(path);
    }

    public void markResolved(String path) {
        missingMandatory.remove(path);
    }

    @Override
    public String toString() {
        return String.valueOf(threshold);
    }
}