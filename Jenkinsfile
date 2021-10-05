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

def buildStage(final String jdkLabel, final String nodeLabel, final boolean isMainBuild) {
    return {
	    stage("Maven Build (${jdkLabel}, ${nodeLabel}") {
	        node(label: nodeLabel) {
	            timeout(60) {
	                checkout scm
	                // TODO: sonarqube
	                // always build with Java 11 (that is the minimum version supported: https://sonarcloud.io/documentation/appendices/end-of-support/)
	                withMaven(maven: 'maven_3_latest', 
	                          jdk: jdkLabel,
	                          publisherStrategy: 'IMPLICIT') {
	                          if (isUnix()) {
	                                 sh  "mvn -U clean verify"
	                          } else {
	                                bat "mvn -U clean verify"
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
            stageMap["JDK ${jdkVersion}, Node label ${nodeLabel}"] = buildStage(availableJDKs[jdkVersion], nodeLabel, isMainBuild)
        }
    }
    return stageMap
}

// https://cwiki.apache.org/confluence/display/INFRA/ci-builds.apache.org
parallel stagesFor([8, 11, 17], 11, [ "ubuntu", "Windows"], "ubuntu")