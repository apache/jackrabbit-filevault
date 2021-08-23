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

import javax.jcr.InvalidItemStateException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.ConstraintViolationException;

import org.apache.jackrabbit.vault.fs.spi.ProgressTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code AutoSave}...
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
     * number of modified nodes to wait for a save retry (only set to a value != 0 after failed save).
     */
    private int failedSaveThreshold = 0;

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
        ret.debugFailEach = debugFailEach;
        // don't copy save count, otherwise it will fail for ever.
        // ret.debugSaveCount = debugSaveCount;
        return ret;
    }

    public void setTracker(@Nullable ProgressTracker tracker) {
        this.tracker = tracker;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * Debug settings to allows to produce failures after each {@code debugFailEach} save.
     * @param debugFailEach cardinal indicating when to fail 
     */
    public void setDebugFailEach(int debugFailEach) {
        this.debugFailEach = debugFailEach;
    }

    /**
     * Returns {@code true} if more than {@link #getThreshold()} nodes are
     * modified.
     * @return {@code true} if threshold reached.
     */
    public boolean needsSave() {
        return (numModified - lastSave) >= threshold + failedSaveThreshold;
    }

    /**
     * Same as {@link #save(Session, boolean)} with the second argument being {@code true}.
     * @param session
     * @throws RepositoryException
     */
    public void save(@Nullable Session session) throws RepositoryException {
        save(session, true);
    }

    /**
     * saves the changes under the given node and resets the counter
     * @param session the session to save. can be {@code null}
     * @throws RepositoryException if an error occurs.
     */
    public void save(@Nullable Session session, boolean isIntermediate) throws RepositoryException {
        int diff = numModified - lastSave;
        if (isIntermediate) {
            if (threshold == Integer.MAX_VALUE) {
                log.trace("Intermediate save disabled.");
                return;
            }
            log.debug("Threshold of {} reached. {} approx {} transient changes.", 
                    threshold,
                    dryRun ? "dry run, reverting" : "saving",
                    diff
            );
        }
        if (tracker != null) {
            if (dryRun) {
                tracker.track("reverting approx " + diff + " nodes... (dry run)", "");
            } else {
                tracker.track("saving approx " + diff + " nodes...", "");
            }
        }
        // TODO: how can session be null here?
        if (session != null) {
            if (debugFailEach > 0 && debugSaveCount > 0 && debugSaveCount%debugFailEach == 0) {
                String msg = String.format("Debugging provoked failure after %s saves.", debugSaveCount);
                log.error(msg);
                throw new RepositoryException(msg);
            }

            if (!saveWithBackoff(session)) {
                // either retry after some more nodes have been modified or after throttle 
                // retry with next save() after another 10 nodes have been modified
                failedSaveThreshold = 10;
                log.warn("Retry auto-save after {} modified nodes", failedSaveThreshold);
            }
        }
        lastSave = numModified;
        failedSaveThreshold = 0;
    }

    /**
     * 
     * @param session
     * @return {@code true} in case was successful or {@code false} in case it failed with a potentially recoverable {@link RepositoryException}
     * @throws RepositoryException in case of unrecoverable exceptions
     */
    private boolean saveWithBackoff(@NotNull Session session) throws RepositoryException {
        try {
            if (dryRun) {
                session.refresh(false);
            } else {
                try {
                    session.save();
                } catch (RepositoryException e) {
                    log.error("error during auto save: {} - retrying after refresh...", e.getMessage());
                    session.refresh(true);
                    session.save();
                }
                debugSaveCount++;
            }
        } catch (RepositoryException e) {
            if (isPotentiallyTransientException(e)) {
                log.warn("could not auto-save due to potentially transient exception {}", e.getCause());
                log.debug("auto save exception", e);
                return false;
            } else {
                throw e;
            }
        }
        return true;
    }

    boolean isPotentiallyTransientException(RepositoryException e) {
        if (e instanceof InvalidItemStateException || e instanceof ConstraintViolationException) {
            return true;
        }
        return false;
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
     * Adds {@code num} modified
     * @param num number of modified
     * @return {@code true} if threshold is reached
     */
    public boolean modified(int num) {
        numModified+= num;
        return needsSave();
    }

    /**
     * As not working reliably it is simply ignored.
     * @param path
     */
    @Deprecated
    public void markMissing(@NotNull String path) {
    }

    /**
     * As not working reliably it is simply ignored.
     * @param path
     */
    @Deprecated
    public void markResolved(@NotNull String path) {
    }

    @Override
    public String toString() {
        return String.valueOf(threshold);
    }

}
