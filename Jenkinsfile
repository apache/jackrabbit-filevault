#!/usr/bin/env groovy
/**
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

properties([
    buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10')),
    preserveStashes(buildCount: 1)
])

def isOnMainBranch() {
    return env.BRANCH_NAME == 'feature/asf-jenkinsfile' || env.BRANCH_NAME == 'PR-170'
}

def buildStage(final int jdkVersion, final String nodeLabel, final boolean isMainBuild) {
    return {
        // https://cwiki.apache.org/confluence/display/INFRA/JDK+Installation+Matrix
        def availableJDKs = [ 8: 'jdk_1.8_latest', 9: 'jdk_1.9_latest', 10: 'jdk_10_latest', 11: 'jdk_11_latest', 12: 'jdk_12_latest', 13: 'jdk_13_latest', 14: 'jdk_14_latest', 15: 'jdk_15_latest', 16: 'jdk_16_latest', 17: 'jdk_17_latest', 18: 'jdk_18_latest']
        final String jdkLabel = availableJDKs[jdkVersion]
        final String stagingPluginGav = "org.sonatype.plugins:nexus-staging-maven-plugin:1.6.8"
        final String sonarPluginGav = "org.sonarsource.scanner.maven:sonar-maven-plugin:3.9.0.2155"
        node(label: nodeLabel) {
            stage("${isMainBuild ? 'Main ' : ''}Maven Build (JDK ${jdkVersion}, ${nodeLabel})") {
                timeout(60) {
                    echo "Running on node ${env.NODE_NAME}"
                    checkout scm
                    String mavenArguments
                    if (isMainBuild) {
                        mavenArguments = "-U -B clean install site ${stagingPluginGav}:deploy -DskipTests -DskipRemoteStaging=true -Pjacoco-report -Dlogback.configurationFile=vault-core/src/test/resources/logback-only-errors.xml"
                    } else {
                        mavenArguments = '-U -B clean verify site -DskipTests'
                    }
                    withMaven(
                        maven: 'maven_3_latest', 
                        jdk: jdkLabel,
                        mavenLocalRepo: '.repository',
                        publisherStrategy: 'IMPLICIT') {
                        if (isUnix()) {
                            sh  "mvn ${mavenArguments}"
                        } else {
                            bat "mvn ${mavenArguments}"
                        }
                    }
                    
                    
                    if (isMainBuild && isOnMainBranch()) {
                        // Stash the build results so we can deploy them on another node
                        stash name: 'filevault-build-snapshots', includes: 'target/nexus-staging/**'
                    }
                }
            }
            if (isMainBuild && isOnMainBranch()) {
                stage("SonarCloud Analysis") {
                    timeout(60) {
                        withCredentials([string(credentialsId: 'sonarcloud-filevault-token', variable: 'SONAR_TOKEN')]) {
                            withMaven(
                                maven: 'maven_3_latest', 
                                jdk: jdkLabel,
                                mavenLocalRepo: '.repository', // reuse repo from previous stage (i.e. must be the same node)
                                publisherStrategy: 'EXPLICIT') {
                                String mavenArguments = "-B ${sonarPluginGav}:sonar -Dsonar.host.url=https://sonarcloud.io -Dsonar.organization=apache -Dsonar.projectKey=apache_jackrabbit-filevault"
                                if (isUnix()) {
                                    sh  "mvn ${mavenArguments}"
                                } else {
                                    bat "mvn ${mavenArguments}"
                                }
                            }
                        }
                    }
                }
            }
        }
        if (isMainBuild && isOnMainBranch()) {
            stage("Deployment") {
                node('nexus-deploy') {
                    timeout(60) {
                        // nexus deployment needs pom.xmlß
                        checkout scm
                        // Unstash the previously stashed build results.
                        unstash name: 'filevault-build-snapshots'
                        withMaven(
                            maven: 'maven_3_latest', 
                            jdk: jdkLabel,
                            mavenLocalRepo: '.repository',
                            publisherStrategy: 'EXPLICIT') {
                            // https://github.com/sonatype/nexus-maven-plugins/tree/master/staging/maven-plugin#deploy-staged
                            String mavenArguments = "${stagingPluginGav}:deploy-staged -DskipStaging=true"
                            if (isUnix()) {
                                sh  "mvn ${mavenArguments}"
                            } else {
                                bat "mvn ${mavenArguments}"
                            }
                        }
                    }
                }
            }
        }
    }
}

def stagesFor(List<Integer> jdkVersions, int mainJdkVersion, List<String> nodeLabels, String mainNodeLabel) {
    def stageMap = [:]
    for (nodeLabel in nodeLabels) {
        for (jdkVersion in jdkVersions) {
            boolean isMainBuild = (jdkVersion == mainJdkVersion && nodeLabel == mainNodeLabel)
            stageMap["JDK ${jdkVersion}, ${nodeLabel}${isMainBuild ? ' (Main)' : ''}"] = buildStage(jdkVersion, nodeLabel, isMainBuild)
        }
    }
    return stageMap
}

// https://cwiki.apache.org/confluence/display/INFRA/ci-builds.apache.org
parallel stagesFor([11/*, 8, 17*/], 11, [ "ubuntu"/*, "Windows"*/], "ubuntu")