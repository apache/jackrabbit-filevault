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
    buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10'))
])

def isOnMainBranch() {
    return BRANCH_NAME == 'master'
}


def buildStage(final String jdkLabel, final String nodeLabel, final boolean isMainBuild) {
    return {
        stage("${isMainBuild ? 'Main ' : ''}Maven Build (${jdkLabel}, ${nodeLabel})") {
            node(label: nodeLabel) {
                timeout(60) {
                    checkout scm
                    String mavenArguments
                    if (isMainBuild) {
                        mavenArguments = '-U -B clean deploy site sonar:sonar -B -Pjacoco-report -Dsonar.host.url=https://sonarcloud.io -Dsonar.organization=apache -Dsonar.projectKey=apache_jackrabbit-filevault -DaltDeploymentRepository=snapshot-repo::default::file:./local-snapshots-dir -Dlogback.configurationFile=vault-core/src/test/resources/logback-only-errors.xml'
                    } else {
                        mavenArguments = '-U -B clean verify site'
                    }

                    withCredentials([string(credentialsId: 'sonarcloud-filevault-token', variable: 'SONAR_TOKEN')]) {
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
                    }
                    if (isMainBuild && isOnMainBranch()) {
                        // Stash the build results so we can deploy them on another node
                        stash name: 'filevault-build-snapshots', includes: 'local-snapshots-dir/**'
                    }
                }
            }
        }
        if (isMainBuild && isOnMainBranch()) {
            stage("Deployment") {
                node('nexus-deploy') {
                    timeout(60) {
                        // Unstash the previously stashed build results.
                        unstash name: 'filevault-build-snapshots'
                        
                        withMaven(
                            maven: 'maven_3_latest', 
                            jdk: jdkLabel,
                            mavenLocalRepo: '.repository',
                            publisherStrategy: 'IMPLICIT') {
                            // https://github.com/sonatype/nexus-maven-plugins/tree/master/staging/maven-plugin#deploy-staged-repository
                            String mavenArguments = 'org.sonatype.plugins:nexus-staging-maven-plugin:1.6.8:deploy-staged-repository -DrepositoryDirectory=./local-snapshots-dir -DnexusUrl=https://repository.apache.org/content/repositories/snapshots -DserverId=apache.snapshots.https'
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
    // https://cwiki.apache.org/confluence/display/INFRA/JDK+Installation+Matrix
    def availableJDKs = [ 8: 'jdk_1.8_latest', 9: 'jdk_1.9_latest', 10: 'jdk_10_latest', 11: 'jdk_11_latest', 12: 'jdk_12_latest', 13: 'jdk_13_latest', 14: 'jdk_14_latest', 15: 'jdk_15_latest', 16: 'jdk_16_latest', 17: 'jdk_17_latest', 18: 'jdk_18_latest']
    def stageMap = [:]
    for (nodeLabel in nodeLabels) {
        for (jdkVersion in jdkVersions) {
            boolean isMainBuild = (jdkVersion == mainJdkVersion && nodeLabel == mainNodeLabel)
            stageMap["JDK ${jdkVersion}, ${nodeLabel}${isMainBuild ? ' (Main)' : ''}"] = buildStage(availableJDKs[jdkVersion], nodeLabel, isMainBuild)
        }
    }
    return stageMap
}

// https://cwiki.apache.org/confluence/display/INFRA/ci-builds.apache.org
parallel stagesFor([8, 11, 17], 11, [ "ubuntu", "Windows"], "ubuntu")