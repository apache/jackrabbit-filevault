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
package org.apache.jackrabbit.vault.rcp.impl;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// Don't call any functions when serializing or deserializing.
// Only look at the class variables of any visibility (including private)
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE,
        creatorVisibility = JsonAutoDetect.Visibility.NONE,
        fieldVisibility = JsonAutoDetect.Visibility.ANY)
public abstract class ConnectionOptionsMixin {
    @JsonCreator
    public ConnectionOptionsMixin(
            @JsonProperty("useSystemProperties")boolean isUseSystemPropertes, 
            @JsonProperty("maxConnections")int maxConnections, 
            @JsonProperty("allowSelfSignedCertificates")boolean isAllowSelfSignedCertificates, 
            @JsonProperty("disableHostnameVerification") boolean isDisableHostnameVerification, 
            @JsonProperty("connectionTimeoutMs") int connectionTimeoutMs, 
            @JsonProperty("requestTimeoutMs") int requestTimeoutMs, 
            @JsonProperty("socketTimeoutMs") int socketTimeoutMs, 
            @JsonProperty("proxyHost") String proxyHost, 
            @JsonProperty("proxyPort") int proxyPort, 
            @JsonProperty("proxyProtocol") String proxyProtocol, 
            @JsonProperty("proxyUsername") String proxyUsername, 
            @JsonProperty("proxyPassword") String proxyPassword) {}
}
