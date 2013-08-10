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
import java.util.EnumMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.util.MimeTypes;
import org.apache.jackrabbit.vault.vlt.VltException;
import org.apache.jackrabbit.vault.vlt.meta.MetaFile;
import org.apache.jackrabbit.vault.vlt.meta.VltEntry;
import org.apache.jackrabbit.vault.vlt.meta.VltEntryInfo;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * <code>Entry</code>...
 *
 */
public class XmlEntry implements VltEntry {

    public static final String CONFLICT_NAME_BASE = ".base";
    public static final String CONFLICT_NAME_MINE = ".mine";
    public static final String CONFLICT_NAME_THEIRS = ".theirs";

    public static final String EN_ENTRY = "entry";

    public static final String AN_NAME = "name";

    public static final String AN_PATH = "rp";

    public static final String AN_AGGREGATE_PATH = "ap";

    private final String name;

    private final String repoRelPath;

    private final String aggregatePath;
    
    private boolean dirty;

    private Map<VltEntryInfo.Type, VltEntryInfo> infos =
            new EnumMap<VltEntryInfo.Type, VltEntryInfo>(VltEntryInfo.Type.class);

    protected XmlEntry(String name, String aggPath, String repoRelPath) {
        this.name = name;
        this.aggregatePath = aggPath;
        this.repoRelPath = repoRelPath;
    }

    public String getName() {
        return name;
    }

    public String getRepoRelPath() {
        return repoRelPath;
    }

    public String getAggregatePath() {
        return aggregatePath;
    }

    public VltEntryInfo create(VltEntryInfo.Type type) {
        return new XmlEntryInfo(type);
    }

    public void put(VltEntryInfo info) {
        if (info != null) {
            dirty = true;
            infos.put(info.getType(), info);
        }
    }

    public VltEntryInfo work() {
        return infos.get(VltEntryInfo.Type.WORK);
    }

    public VltEntryInfo base() {
        return infos.get(VltEntryInfo.Type.BASE);
    }

    public VltEntryInfo mine() {
        return infos.get(VltEntryInfo.Type.MINE);
    }

    public VltEntryInfo theirs() {
        return infos.get(VltEntryInfo.Type.THEIRS);
    }

    public VltEntryInfo remove(VltEntryInfo.Type type) {
        dirty = true;
        return infos.remove(type);
    }

    public State getState() {
        if (infos.containsKey(VltEntryInfo.Type.MINE)) {
            return State.CONFLICT;
        }
        if (!infos.containsKey(VltEntryInfo.Type.BASE)) {
            return State.ADDED;
        }
        if (!infos.containsKey(VltEntryInfo.Type.WORK)) {
            return State.DELETED;
        }
        return State.CLEAN;
    }


    public void resolved(MetaFile fileTmp, File fileWork, MetaFile fileBase)
            throws IOException {
        // cleanup files
        XmlEntryInfo mine = (XmlEntryInfo) mine();
        XmlEntryInfo base = (XmlEntryInfo) base();
        XmlEntryInfo theirs = (XmlEntryInfo) theirs();
        XmlEntryInfo work = (XmlEntryInfo) work();

        // delete the .x files
        new File(fileWork.getParentFile(), base.getName()).delete();
        new File(fileWork.getParentFile(), mine.getName()).delete();
        new File(fileWork.getParentFile(), theirs.getName()).delete();

        // copy the tmp file to the base
        File tmp = fileBase.openTempFile();
        fileTmp.copyTo(tmp, true);
        fileBase.closeTempFile(false);

        // and update the infos
        base.update(theirs);
        base.setName(null);
        work.update(fileWork, true);
        remove(mine.getType());
        remove(theirs.getType());
    }

    public boolean delete(File fileWork) {
        // cleanup files
        XmlEntryInfo mine = (XmlEntryInfo) mine();
        XmlEntryInfo base = (XmlEntryInfo) base();
        XmlEntryInfo theirs = (XmlEntryInfo) theirs();

        // delete the .x files
        if (mine != null) {
            new File(fileWork.getParentFile(), mine.getName()).delete();
        }
        if (theirs != null) {
            new File(fileWork.getParentFile(), theirs.getName()).delete();
        }
        if (base != null && base.getName() != null) {
            new File(fileWork.getParentFile(), base.getName()).delete();
        }
        fileWork.delete();
        if (base != null) {
            base.setName(null);
        }
        remove(VltEntryInfo.Type.MINE);
        remove(VltEntryInfo.Type.THEIRS);
        remove(VltEntryInfo.Type.WORK);

        return infos.isEmpty();
    }

    public boolean revertConflict(File work) throws IOException {
        File dir = work.getParentFile();

        XmlEntryInfo mine = (XmlEntryInfo) mine();
        XmlEntryInfo theirs = (XmlEntryInfo) theirs();
        XmlEntryInfo base = (XmlEntryInfo) base();

        File fileMine = new File(dir, mine.getName());
        if (!fileMine.exists()) {
            return false;
        }
        // copy and delete files
        FileUtils.copyFile(fileMine, work);
        fileMine.delete();
        new File(dir, theirs.getName()).delete();
        new File(dir, base.getName()).delete();

        // remove infos
        remove(mine.getType());
        remove(theirs.getType());
        base.setName(null);

        // hack: fix content type if it was lost
        if (mine.getContentType() == null && base.getContentType() == null) {
            VltEntryInfo workInfo = work();
            if (workInfo.getContentType() == null) {
                workInfo.setContentType(MimeTypes.getMimeType(work.getName()));
            }
            base.setContentType(workInfo.getContentType());
        }
        return true;
    }

    public void conflict(File work, MetaFile base, MetaFile tmp)
            throws IOException {
        File dir = work.getParentFile();
        File fileMine = new File(dir, name + CONFLICT_NAME_MINE);
        File fileBase = new File(dir, name + CONFLICT_NAME_BASE);
        File fileTheirs = new File(dir, name + CONFLICT_NAME_THEIRS);

        // copy the 3 files
        FileUtils.copyFile(work, fileMine);
        base.copyTo(fileBase, true);
        tmp.copyTo(fileTheirs, true);

        // the base gets and additional name
        ((XmlEntryInfo) base()).setName(fileBase.getName());

        // the 'work' becomes the 'mine'
        String contentType = base().getContentType();
        XmlEntryInfo mine = new XmlEntryInfo(VltEntryInfo.Type.MINE);
        mine.update(fileMine, true);
        mine.setName(fileMine.getName());
        mine.setContentType(contentType);
        put(mine);

        // add the 'theirs' as well
        XmlEntryInfo theirs = new XmlEntryInfo(VltEntryInfo.Type.THEIRS);
        theirs.update(fileTheirs, true);
        theirs.setName(fileTheirs.getName());
        theirs.setContentType(contentType);
        put(theirs);
    }

    public void write(ContentHandler handler) throws SAXException {
        AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute("", AN_NAME, "", "CDATA", name);
        if (repoRelPath != null) {
            attrs.addAttribute("", AN_PATH, "", "CDATA", repoRelPath);
        }
        if (aggregatePath != null) {
            attrs.addAttribute("", AN_AGGREGATE_PATH, "", "CDATA", aggregatePath);
        }
        handler.startElement("", EN_ENTRY, "", attrs);
        for (VltEntryInfo info: infos.values()) {
            ((XmlEntryInfo) info).write(handler);
        }
        handler.endElement("", EN_ENTRY, "");
        dirty = false;
    }

    public boolean isDirty() {
        if (dirty) {
            return true;
        }
        for (VltEntryInfo info: infos.values()) {
            if (((XmlEntryInfo) info).isDirty()) {
                return dirty = true;
            }
        }
        return false;
    }

    public boolean isDirectory() {
        VltEntryInfo base = infos.get(VltEntryInfo.Type.BASE);
        if (base != null) {
            return base.isDirectory();
        }
        VltEntryInfo work = infos.get(VltEntryInfo.Type.WORK);
        return work != null && work.isDirectory();
    }

    protected static XmlEntry load(Element elem)
            throws VltException {
        assert elem.getNodeName().equals(EN_ENTRY);
        String name = elem.getAttribute(AN_NAME);
        if (name == null) {
            throw new VltException("entry has no '" + AN_NAME + "' attribute");
        }
        String path = elem.hasAttribute(AN_PATH) ? elem.getAttribute(AN_PATH) : null;
        String ap = elem.hasAttribute(AN_AGGREGATE_PATH) ? elem.getAttribute(AN_AGGREGATE_PATH) : null;

        XmlEntry entry = new XmlEntry(name, ap, path);

        // add infos
        NodeList nodes = elem.getChildNodes();
        for (int i=0; i<nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element) {
                entry.put(XmlEntryInfo.load((Element) node));
            }
        }
        entry.dirty = false;
        return entry;
    }
}