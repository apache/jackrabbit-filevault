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

package org.apache.jackrabbit.vault.vlt.meta.xml;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.util.MD5;
import org.apache.jackrabbit.vault.vlt.VltException;
import org.apache.jackrabbit.vault.vlt.meta.MetaFile;
import org.apache.jackrabbit.vault.vlt.meta.VltEntryInfo;
import org.w3c.dom.Element;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * <code>Entry</code>...
 *
 */
public class XmlEntryInfo implements VltEntryInfo {

    private static final String AN_NAME = "name";
    private static final String AN_DATE = "date";
    private static final String AN_MD5 = "md5";
    private static final String AN_CONTENT_TYPE = "contentType";
    private static final String AN_SIZE = "size";

    private final Type type;

    private String name;

    private long date;

    private MD5 md5;

    private String contentType;

    private long size;

    private boolean dirty;

    public XmlEntryInfo(Type type) {
        this.type = type;
    }

    public VltEntryInfo copyAs(Type type) {
        XmlEntryInfo info = new XmlEntryInfo(type);
        info.update(this);
        return info;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (this.name == null && name != null
                || this.name != null && !this.name.equals(name)) {
            this.name = name;
            dirty = true;
        }
    }

    public Type getType() {
        return type;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        // round to second
        date -= date % 1000;
        if (date != this.date) {
            this.date = date;
            dirty = true;
        }
    }

    public MD5 getMd5() {
        return md5;
    }

    public void setMd5(MD5 md5) {
        if (this.md5 == null && md5 != null
                || this.md5 != null && !this.md5.equals(md5)) {
            this.md5 = md5;
            dirty = true;
        }
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        if (this.contentType == null && contentType != null
                || this.contentType != null && !this.contentType.equals(contentType)) {
            this.contentType = contentType;
            dirty = true;
        }
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        if (size != this.size) {
            this.size = size;
            dirty = true;
        }
    }

    /**
     * Checks if the remote file is modified compared to this entry.
     * It is modified if:
     * - the local time is 0
     * - the remote time is 0 or greater than this time.
     * - the size differs
     * - the content type differs
     *
     * @param remoteFile the remote file to compare with
     * @return <code>true</code> if modified.
     */
    public boolean checkModified(VaultFile remoteFile) {
        long rTime = remoteFile.lastModified();
        rTime -= rTime % 1000;
        
        if (date <=0 || rTime <= 0 || rTime > date) {
            return true;
        }
        long rSize = remoteFile.length();
        if (rSize < 0 || rSize != size) {
            return true;
        }
        String ct = remoteFile.getContentType();
        return ct == null || !ct.equals(contentType);
    }


    public void update(VltEntryInfo base) {
        setName(((XmlEntryInfo) base).getName());
        setDate(base.getDate());
        setMd5(base.getMd5());
        setContentType(base.getContentType());
        setSize(base.getSize());
    }

    public void update(File file, boolean force) throws IOException {
        if (file.isDirectory()) {
            dirty = size != 0 && md5 != null && contentType != null;
            if (dirty) {
                size = 0;
                md5 = null;
                contentType = null;
                date = file.lastModified();
            }
        } else {
            if (force || file.lastModified() != date || file.length() != size) {
                size = file.length();
                md5 = MD5.digest(file);
                date = file.lastModified();
                dirty = true;
            }
        }
    }

    public void update(MetaFile file, boolean force) throws IOException {
        if (force || file.lastModified() > date) {
            size = file.length();
            md5 = file.md5();
            date = file.lastModified();
            dirty = true;
        }
    }

    public boolean isDirectory() {
        return size == 0 && md5 == null && contentType == null;
    }

    public boolean isDirty() {
        return dirty;
    }

    public boolean isSame(VltEntryInfo base) {
        return size == base.getSize()
                && ((md5 == null && base.getMd5() == null)
                    || md5 != null && md5.equals(base.getMd5()));
    }

    public void write(ContentHandler handler) throws SAXException {
        AttributesImpl attrs = new AttributesImpl();
        addAttributes(attrs);
        handler.startElement("", type.name().toLowerCase(), "", attrs);
        handler.endElement("", type.name().toLowerCase(), "");
        dirty = false;
    }

    protected void addAttributes(AttributesImpl attrs) {
        if (name != null) {
            attrs.addAttribute("", AN_NAME, "", "CDATA", name);
        }
        if (date > 0) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(date);
            attrs.addAttribute("", AN_DATE, "", "CDATA", ISO8601.format(c));
        }
        if (md5 != null) {
            attrs.addAttribute("", AN_MD5, "", "CDATA", md5.toString());
        }
        if (contentType != null) {
            attrs.addAttribute("", AN_CONTENT_TYPE, "", "CDATA", contentType);
        }
        if (size > 0) {
            attrs.addAttribute("", AN_SIZE, "", "CDATA", String.valueOf(size));
        }
    }

    protected static VltEntryInfo load(Element elem)
            throws VltException {
        Type type;
        try {
            type = Type.valueOf(elem.getNodeName().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new VltException("unknown entry type '" + elem.getNodeName() + "'");
        }
        XmlEntryInfo entry = new XmlEntryInfo(type);
        if (elem.hasAttribute(AN_NAME)) {
            entry.setName(elem.getAttribute(AN_NAME));
        }
        if (elem.hasAttribute(AN_DATE)) {
            entry.setDate(ISO8601.parse(elem.getAttribute(AN_DATE)).getTime().getTime());
        }
        if (elem.hasAttribute(AN_MD5)) {
            try {
                entry.setMd5(new MD5(elem.getAttribute(AN_MD5)));
            } catch (Exception e) {
                // ignore
            }
        }
        if (elem.hasAttribute(AN_CONTENT_TYPE)) {
            entry.setContentType(elem.getAttribute(AN_CONTENT_TYPE));
        }
        if (elem.hasAttribute(AN_SIZE)) {
            try {
                entry.setSize(Integer.parseInt(elem.getAttribute(AN_SIZE)));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        entry.dirty = false;
        return entry;
    }


}