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

package org.apache.jackrabbit.vault.util;

import java.io.IOException;
import java.io.StringReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Entity resolver that handles all entity resolution requests by returning an empty input source.
 * This is to prevent "Arbitrary DTD inclusion in XML parsing".
 */
public class RejectingEntityResolver implements EntityResolver {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(RejectingEntityResolver.class);

    public InputSource resolveEntity(String publicId, String systemId)
            throws SAXException, IOException {
        log.warn("Rejecting external entity loading with publicId={} systemId={}", publicId, systemId);
        return new InputSource(new StringReader(""));
    }

}