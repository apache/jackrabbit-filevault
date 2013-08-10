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
package org.apache.jackrabbit.vault.vlt.actions;

import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.vlt.VltDirectory;
import org.apache.jackrabbit.vault.vlt.VltException;
import org.apache.jackrabbit.vault.vlt.VltFile;

/**
 * <code>AbstractAction</code>...
 *
 */
abstract public class AbstractAction implements Action {

    private boolean onlyControlled;

    public boolean onlyControlled() {
        return onlyControlled;
    }

    public void setOnlyControlled(boolean onlyControlled) {
        this.onlyControlled = onlyControlled;
    }

    /**
     * {@inheritDoc}
     */
    public void run(VltDirectory dir, VltFile file, VaultFile remoteFile)
            throws VltException {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     *
     * @return <code>true</code> if the given directory is controlled or
     *         {@link #onlyControlled} is <code>false</code>;
     */
    public boolean run(VltDirectory dir, VaultFile remoteDir)
            throws VltException {
        return dir.isControlled() || !onlyControlled;
    }

}