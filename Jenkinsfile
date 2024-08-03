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
// use the shared library from https://github.com/apache/jackrabbit-filevault-jenkins-lib
@Library('filevault@master') _

vaultPipeline('ubuntu', 11, '3', {
  vaultStageSanityCheck()
  vaultStageBuild(['Windows'], [17,21], [], 'apache_jackrabbit-filevault', 
     [
       simpleCredentialsMap: ['NIST_NVD_API_KEY': 'NIST_NVD_API_KEY'],
       mainBuildArguments: '-U clean site deploy -Pjacoco-report,dependency-check -Dlogback.configurationFile=vault-core/src/test/resources/logback-only-errors.xml -DnvdApiKeyEnvironmentVariable=NIST_NVD_API_KEY'
     ]
  )
  vaultStageDeploy()
}
)