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
package org.apache.jackrabbit.vault.validation.spi.util.classloaderurl;

import java.io.IOException;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import org.osgi.annotation.versioning.ProviderType;

@ProviderType
public class URLFactory {
    public static final String TCCL_PROTOCOL_PREFIX = "tccl:";
    
    private URLFactory() {
        
    }

    public static URL createURL(String spec) throws MalformedURLException {
        final URL url;
        // which URLHandler to take
        if (spec.startsWith(TCCL_PROTOCOL_PREFIX)) {
            // use custom UrlStreamHandler
            url = new URL(null, spec, new ThreadContextClassLoaderURLStreamHandler());
        } else {
            // use default UrlStreamHandler
            url = new URL(spec);
        }
        return url;
    }

    public static void processUrlStreams(List<String> urls, Consumer<Reader> readerProcessor) {
        for (String url : urls) {
            try (Reader reader = new InputStreamReader(URLFactory.createURL(url).openStream(), StandardCharsets.US_ASCII)) {
                readerProcessor.accept(reader);
            } catch (IOException e) {
                throw new IllegalArgumentException("Error loading content from " + url, e);
            }
        }
    }
}
