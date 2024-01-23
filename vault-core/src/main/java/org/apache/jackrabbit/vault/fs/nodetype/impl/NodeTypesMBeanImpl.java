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
package org.apache.jackrabbit.vault.fs.nodetype.impl;

import java.io.IOException;
import java.io.StringWriter;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.management.DynamicMBean;
import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = DynamicMBean.class, immediate = true, property = {
        "service.vendor=The Apache Software Foundation",
        "jmx.objectname=org.apache.jackrabbit.vault.fs.nodetype:type=manager"
})
public class NodeTypesMBeanImpl extends StandardMBean implements NodeTypesMBean {

    @Reference
    private SlingRepository slingRepository;

    public NodeTypesMBeanImpl() throws NotCompliantMBeanException {
        super(NodeTypesMBean.class);
    }

    @Override
    public String getNodeTypePropertiesFileForPaths(String... paths) throws RepositoryException, IOException {
        final Session session = slingRepository.loginAdministrative(null);
        try (StringWriter writer = new StringWriter()) {
            PropertesFileRepositoryNodeTypesImpl.createPropertiesFromSession(writer, session, -1, paths);
            return writer.toString();
        }
    }

}
