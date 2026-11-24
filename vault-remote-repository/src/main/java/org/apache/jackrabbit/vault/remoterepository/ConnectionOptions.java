/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.remoterepository;

/**
 * Advanced connection options to use for connections to a remote repository.
 * Use {@link #factory()} to create a new instance of this class.
 *
 */
public final class ConnectionOptions {

    private final boolean useSystemProperties;
    private final int maxConnections;
    private final boolean allowSelfSignedCertificates;
    private final boolean disableHostnameVerification;
    private final String proxyHost;
    private final int proxyPort;
    private final String proxyProtocol;
    private final String proxyUsername;
    private final String proxyPassword;
    private final int connectionTimeoutMs;
    private final int requestTimeoutMs;
    private final int socketTimeoutMs;

    private ConnectionOptions(
            boolean useSystemProperties,
            int maxConnections,
            boolean allowSelfSignedCertificates,
            boolean disableHostnameVerification,
            String proxyHost,
            int proxyPort,
            String proxyProtocol,
            String proxyUsername,
            String proxyPassword,
            int connectionTimeoutMs,
            int requestTimeoutMs,
            int socketTimeoutMs) {
        this.useSystemProperties = useSystemProperties;
        this.maxConnections = maxConnections;
        this.allowSelfSignedCertificates = allowSelfSignedCertificates;
        this.disableHostnameVerification = disableHostnameVerification;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.proxyProtocol = proxyProtocol;
        this.proxyUsername = proxyUsername;
        this.proxyPassword = proxyPassword;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.requestTimeoutMs = requestTimeoutMs;
        this.socketTimeoutMs = socketTimeoutMs;
    }

    public static Factory factory() {
        return new Factory();
    }

    public boolean isUseSystemProperties() {
        return useSystemProperties;
    }

    /**
     * Returns the maximum number of connections to use for this repository connection.
     * A value of -1 indicates that the default value should be used.
     *
     * @return the maximum number of connections to use for this repository connection.
     */
    public int getMaxConnections() {
        return maxConnections;
    }

    /**
     * Returns whether self-signed certificates should be allowed for this repository connection.
     *
     * @return whether self-signed certificates should be allowed for this repository connection.
     */
    public boolean isAllowSelfSignedCertificates() {
        return allowSelfSignedCertificates;
    }

    /**
     * Returns whether hostname verification should be disabled for this repository connection.
     *
     * @return whether hostname verification should be disabled for this repository connection.
     */
    public boolean isDisableHostnameVerification() {
        return disableHostnameVerification;
    }

    /**
     * Returns the proxy host to use for this repository connection, or null if no proxy should be used.
     *
     * @return the proxy host to use for this repository connection, or null if no proxy should be used.
     */
    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * Returns the proxy port to use for this repository connection, or -1 if no proxy should be used.
     *
     * @return the proxy port to use for this repository connection, or -1 if no proxy should be used.
     */
    public int getProxyPort() {
        return proxyPort;
    }

    /**
     * Returns the proxy protocol to use for this repository connection, or null if no proxy should be used.
     *
     * @return the proxy protocol to use for this repository connection, or null if no proxy should be used.
     */
    public String getProxyProtocol() {
        return proxyProtocol;
    }

    /**
     * Returns the proxy username to use for this repository connection, or null if no proxy should be used.
     *
     * @return the proxy username to use for this repository connection, or null if no proxy should be used.
     */
    public String getProxyUsername() {
        return proxyUsername;
    }

    /**
     * Returns the proxy password to use for this repository connection, or null if no proxy should be used.
     *
     * @return the proxy password to use for this repository connection, or null if no proxy should be used.
     */
    public String getProxyPassword() {
        return proxyPassword;
    }

    /**
     * Returns the connection timeout in milliseconds to use for this repository connection, or -1 if the default value should be used.
     *
     * @return the connection timeout in milliseconds to use for this repository connection, or -1 if the default value should be used.
     */
    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    /**
     * Returns the request timeout in milliseconds to use for this repository connection, or -1 if the default value should be used.
     *
     * @return the request timeout in milliseconds to use for this repository connection, or -1 if the default value should be used.
     */
    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    /**
     * Returns the socket timeout in milliseconds to use for this repository connection, or -1 if the default value should be used.
     *
     * @return the socket timeout in milliseconds to use for this repository connection, or -1 if the default value should be used.
     */
    public int getSocketTimeoutMs() {
        return socketTimeoutMs;
    }

    public static final class Factory {

        private boolean useSystemProperties;
        private int maxConnections = -1;
        private boolean allowSelfSignedCertificates;
        private boolean disableHostnameVerification;
        private String proxyHost;
        private int proxyPort = -1;
        private String proxyProtocol;
        private String proxyUsername;
        private String proxyPassword;
        private int connectionTimeoutMs = -1;
        private int requestTimeoutMs = -1;
        private int socketTimeoutMs = -1;

        private Factory() {
            // Intentionally empty; callers configure only the options they need.
        }

        public Factory useSystemProperties(boolean useSystemProperties) {
            this.useSystemProperties = useSystemProperties;
            return this;
        }

        public Factory maxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        public Factory allowSelfSignedCertificates(boolean allowSelfSignedCertificates) {
            this.allowSelfSignedCertificates = allowSelfSignedCertificates;
            return this;
        }

        public Factory disableHostnameVerification(boolean disableHostnameVerification) {
            this.disableHostnameVerification = disableHostnameVerification;
            return this;
        }

        public Factory proxyHost(String proxyHost) {
            this.proxyHost = proxyHost;
            return this;
        }

        public Factory proxyPort(int proxyPort) {
            this.proxyPort = proxyPort;
            return this;
        }

        public Factory proxyProtocol(String proxyProtocol) {
            this.proxyProtocol = proxyProtocol;
            return this;
        }

        public Factory proxyUsername(String proxyUsername) {
            this.proxyUsername = proxyUsername;
            return this;
        }

        public Factory proxyPassword(String proxyPassword) {
            this.proxyPassword = proxyPassword;
            return this;
        }

        public Factory connectionTimeoutMs(int connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
            return this;
        }

        public Factory requestTimeoutMs(int requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
            return this;
        }

        public Factory socketTimeoutMs(int socketTimeoutMs) {
            this.socketTimeoutMs = socketTimeoutMs;
            return this;
        }

        public ConnectionOptions build() {
            return new ConnectionOptions(
                    useSystemProperties,
                    maxConnections,
                    allowSelfSignedCertificates,
                    disableHostnameVerification,
                    proxyHost,
                    proxyPort,
                    proxyProtocol,
                    proxyUsername,
                    proxyPassword,
                    connectionTimeoutMs,
                    requestTimeoutMs,
                    socketTimeoutMs);
        }
    }
}
