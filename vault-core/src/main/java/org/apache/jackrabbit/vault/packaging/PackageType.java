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
package org.apache.jackrabbit.vault.packaging;

/**
 * Specifies the type of the package. The package type helps to characterize the contents of a package and influences
 * how the package is used during deployment, installation and removal.
 */
public enum PackageType {

    /**
     * An application package consists purely of application content. It serializes entire subtrees with no
     * inclusion or exclusion filters. it does not contain any subpackages nor OSGi configuration or bundles.
     */
    APPLICATION,

    /**
     * A content package consists only of content and user defined configuration. It usually serializes entire subtrees
     * but can contain inclusion or exclusion filters. it does not contain any subpackages nor OSGi configuration or bundles.
     */
    CONTENT,

    /**
     * A container package only contains sub packages and OSGi configuration and bundles. The container package is only
     * used as container for deployment.
     */
    CONTAINER,

    /**
     * Catch all type for a combination of the above.
     */
    MIXED

}