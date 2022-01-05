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
package org.apache.jackrabbit.vault.rcp;

import java.util.List;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.spi2dav.ConnectionOptions;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.util.RepositoryCopier;

public interface RcpTask {

    interface Result {
        enum State {
            NEW, RUNNING, ENDED, STOPPING, STOPPED
        }

        State getState();
        /**
         * 
         * @return the exception in case of {@link #getState()} == ENDED and the execution was not successful otherwise {@code null}
         */
        Throwable getThrowable();
    }

    String getId();

    RepositoryAddress getSource();

    String getDestination();

    boolean start(Session session) throws RepositoryException;

    boolean stop();

    RepositoryCopier getRcp();

    boolean isRecursive();

    Result getResult();

    /**
     * 
     * @return either {@code null} in case no excludes are set or a list of excludes (regex patterns)
     */
    List<String> getExcludes();

    /**
     * 
     * @return internal filter used when the content is exported
     */
    WorkspaceFilter getFilter();

    ConnectionOptions getConnectionOptions();

}