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
package org.apache.jackrabbit.vault.packaging.registry.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.MemoryArchive;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.events.PackageEvent;
import org.apache.jackrabbit.vault.packaging.events.impl.PackageEventDispatcher;
import org.apache.jackrabbit.vault.packaging.impl.PackagePropertiesImpl;
import org.apache.jackrabbit.vault.packaging.impl.ZipVaultPackage;
import org.apache.jackrabbit.vault.packaging.registry.DependencyReport;
import org.apache.jackrabbit.vault.packaging.registry.PackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;
import org.apache.jackrabbit.vault.util.InputStreamPump;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.jackrabbit.vault.util.RejectingEntityResolver;
import org.apache.jackrabbit.vault.util.xml.serialize.OutputFormat;
import org.apache.jackrabbit.vault.util.xml.serialize.XMLSerializer;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * FileSystem based registry not depending on a JCR Session. All metadata is stored in Filesystem and can be prepared and used without a running JCR repository
 * Only methods to install or uninstall packages require an active {@code Session} object of running jcr instance to perform the actual installation tasks 
 */
@Component(
        service = PackageRegistry.class,
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        property = {"service.vendor=The Apache Software Foundation"}
)
@Designate(ocd = FSPackageRegistry.Config.class)
public class FSPackageRegistry extends AbstractPackageRegistry {

    private static final String REPOSITORY_HOME = "repository.home";

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(FSPackageRegistry.class);

    private static final String TAG_REGISTRY_METADATA = "registryMetadata";

    private static final String ATTR_PACKAGE_ID = "packageid";

    private static final String ATTR_FILE_PATH = "filepath";

    private static final String ATTR_PACKAGE_STATUS = "packagestatus";

    private static final String ATTR_EXTERNAL = "external";

    private static final String TAG_DEPENDENCY = "dependency";
    
    /**
     * Suffixes for metadata files
     */
    private final String[] META_SUFFIXES = {"xml"};
    
    private Map<PackageId, InstallState> stateCache = new ConcurrentHashMap<>();

    private boolean packagesInitializied = false; 

    @Nullable
    private PackageEventDispatcher dispatcher;

    private File homeDir;
    
    public File getHomeDir() {
        return homeDir;
    }

    /**
     * Creates a new FSPackageRegistry based on the given home directory.
     * 
     * @param homeDir the directory in which packages and their metadata is stored
     * @throws IOException
     */
    public FSPackageRegistry(@Nonnull File homeDir) throws IOException {
        this.homeDir = homeDir;
    }
    
    /**
     * Deafult constructor for OSGi initialization (homeDir defined via activator)
     */
    public FSPackageRegistry() {
    }
    
    
    @ObjectClassDefinition(
            name = "Apache Jackrabbit FS Package Registry Service"
    )
    @interface Config {
        
        @AttributeDefinition
        String homePath() default "packageregistry";
    }

    @Activate
    private void activate(BundleContext context, Config config) {
        String repoHome = context.getProperty(REPOSITORY_HOME);
        this.homeDir = new File(repoHome + "/" + config.homePath());
        if (!homeDir.exists()) {
            homeDir.mkdirs();
        }
        log.info("Jackrabbit Filevault FS Package Registry initialized with home location {}", this.homeDir.getPath());
    }

    /**
     * Sets the event dispatcher
     * 
     * @param dispatcher
     *            the dispatcher.
     */
    public void setDispatcher(@Nullable PackageEventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Dispatches a package event using the configured dispatcher.
     * 
     * @param type
     *            event type
     * @param id
     *            package id
     * @param related
     *            related packages
     */
    public void dispatch(@Nonnull PackageEvent.Type type, @Nonnull PackageId id, @Nullable PackageId[] related) {
        if (dispatcher == null) {
            return;
        }
        dispatcher.dispatch(type, id, related);
    }

    @Nullable
    @Override
    public RegisteredPackage open(@Nonnull PackageId id) throws IOException {
        try {
            File pkg = getPackageFile(id);
            return pkg.exists() ? new FSRegisteredPackage(this, open(pkg)) : null;
        } catch (RepositoryException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean contains(@Nonnull PackageId id) throws IOException {
        File meta = getPackageMetaDataFile(id);
        return meta.exists() && meta.isFile();
    }

    @Nullable
    private File getPackageFile(@Nonnull PackageId id) {
        try {
            InstallState state = getInstallState(id);
            if (FSPackageStatus.NOTREGISTERED.equals(state.getStatus())) {
                return buildPackageFile(id);
            } else {
                return new File(state.getFilePath());
            }
        } catch (IOException e) {
            log.error("Couldn't get install state of packageId {}", id, e);
        }
        return null;
    }
    
    private File buildPackageFile(@Nonnull PackageId id) {
        String path = getInstallationPath(id);
        return new File(getHomeDir(),path + ".zip");
    }
    
    @Nullable
    private File getPackageMetaDataFile(@Nonnull PackageId id) {
        String path = getInstallationPath(id);
        return new File(getHomeDir(),path + ".xml");
    }

    /**
     * {@inheritDoc}
     */
    public VaultPackage open(File pkg) throws RepositoryException {
        try {
            return new ZipVaultPackage(pkg, false, true);
        } catch (IOException e) {
            log.error("Cloud not open file {} as ZipVaultPackage.", pkg.getPath(), e);
            return null;
        }

    }
    
    @Nonnull
    @Override
    public DependencyReport analyzeDependencies(@Nonnull PackageId id, boolean onlyInstalled) throws IOException, NoSuchPackageException {
        List<Dependency> unresolved = new LinkedList<Dependency>();
        List<PackageId> resolved = new LinkedList<PackageId>();
        InstallState state = getInstallState(id);
        if (FSPackageStatus.NOTREGISTERED.equals(state.getStatus())) {
            throw new NoSuchPackageException().setId(id);
        }

        for (Dependency dep : state.getDependencies()) {
            PackageId resolvedId = resolve(dep, onlyInstalled);
            if (resolvedId == null) {
                unresolved.add(dep);
            } else {
                resolved.add(resolvedId);
            }
        }
        

        return new DependencyReportImpl(id, unresolved.toArray(new Dependency[unresolved.size()]),
                resolved.toArray(new PackageId[resolved.size()])
        );
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public PackageId resolve(Dependency dependency, boolean onlyInstalled) throws IOException {
        PackageId bestId = null;
        for (PackageId id : packages()) {
            if (!onlyInstalled || isInstalled(id)) {
                if (dependency.matches(id)) {
                    if (bestId == null || id.getVersion().compareTo(bestId.getVersion()) > 0) {
                        bestId = id;
                    }
                }
            }
        }
        return bestId;
    }

    boolean isInstalled(PackageId id) throws IOException {
        FSPackageStatus status = getInstallState(id).getStatus();
        return FSPackageStatus.EXTRACTED.equals(status) || FSPackageStatus.INSTALLED.equals(status);
    }

    @Nonnull
    @Override
    public PackageId register(@Nonnull InputStream in, boolean replace) throws IOException, PackageExistsException {
        ZipVaultPackage pkg = upload(in, replace);
        
        extractSubPackages(pkg.getArchive(), replace);
        File pkgFile = buildPackageFile(pkg.getId());
        setInstallState(pkg.getId(), FSPackageStatus.REGISTERED, pkgFile.getPath(), false, pkg.getDependencies());
        return pkg.getId();
    }
    
    private void extractSubPackages(Archive archive, boolean replace)
            throws IOException, PackageExistsException {
        try {
        Archive.Entry packagesRoot = archive.getJcrRoot().getChild("etc").getChild("packages");
        extractSubPackages(archive, packagesRoot, "/etc/packages", replace);
        } catch (NullPointerException e) {
            // nothing to do - expected if subpath isn't available
        }
    
       }
    private void extractSubPackages(Archive archive, Archive.Entry directory, String parentPath, boolean replace)
            throws IOException, PackageExistsException {
        Collection<? extends Archive.Entry> files = directory.getChildren();

        for (Archive.Entry file: files) {
            String fileName = file.getName();
            String repoName = PlatformNameFormat.getRepositoryName(fileName);
            String repoPath = parentPath + "/" + repoName;
            if (file.isDirectory()) {
                extractSubPackages(archive, file, repoPath, replace);
            } else {
                if (repoPath.startsWith(JcrPackageRegistry.DEFAULT_PACKAGE_ROOT_PATH_PREFIX) && (repoPath.endsWith(".jar") || repoPath.endsWith(".zip"))) {
                    try {
                        register(archive.openInputStream(file), replace);
                    } catch (PackageExistsException e) {
                        log.info("Subpackage already registered, skipping subpackage extraction.");
                    }
                }
            }
        }
    }

    public ZipVaultPackage upload(InputStream in, boolean replace)
            throws IOException, PackageExistsException {

        MemoryArchive archive = new MemoryArchive(false);
        File tempFile = File.createTempFile("upload", ".zip");
        
        InputStreamPump pump = new InputStreamPump(in , archive);
        try {
            // this will cause the input stream to be consumed and the memory
            // archive being initialized.
            try {

                FileUtils.copyInputStreamToFile(pump, tempFile);
            } catch (Exception e) {
                String msg = "Stream could be read successfully.";
                log.error(msg);
                throw new IOException(msg, e);
            }
    
            if (archive.getJcrRoot() == null) {
                String msg = "Stream is not a content package. Missing 'jcr_root'.";
                log.error(msg);
                throw new IOException(msg);
            }
    
            final MetaInf inf = archive.getMetaInf();
            PackagePropertiesImpl props = new PackagePropertiesImpl() {
                @Override
                protected Properties getPropertiesMap() {
                    return inf.getProperties();
                }
            };
            PackageId pid = props.getId();
    
            // invalidate pid if path is unknown
            if (pid == null) {
                throw new IllegalArgumentException("Unable to create package. No package pid set.");
            }
            if (!pid.isValid()) {
                throw new IllegalArgumentException("Unable to create package. Illegal package name.");
            }
    
            File oldPkgFile = getPackageFile(pid);
            InstallState state = getInstallState(pid);

            if (oldPkgFile.exists()) {
                if (replace && !state.isExternal()) {
                    oldPkgFile.delete();
                } else {
                    throw new PackageExistsException("Package already exists: " + pid).setId(pid);
                }
            }
            
            ZipVaultPackage pkg = new ZipVaultPackage(archive, true);
            extractSubPackages(pkg.getArchive(), replace);
            File pkgFile = buildPackageFile(pid);
            FileUtils.moveFile(tempFile, pkgFile);
            dispatch(PackageEvent.Type.UPLOAD, pid, null);
            return pkg;
        } finally {
            pump.close();
        }

    }

    @Nonnull
    @Override
    public PackageId register(@Nonnull File file, boolean replace) throws IOException, PackageExistsException {
        ZipVaultPackage pack = new ZipVaultPackage(file, false, true);
        try {
            File pkgFile = buildPackageFile(pack.getId());
            if (pkgFile.exists()) {
                if (replace) {
                    pkgFile.delete();
                } else {
                    throw new PackageExistsException("Package already exists: " + pack.getId()).setId(pack.getId());
                }
            }
            extractSubPackages(pack.getArchive(), replace);
            FileUtils.copyFile(file, pkgFile);
            setInstallState(pack.getId(), FSPackageStatus.REGISTERED, pkgFile.getPath(), false, pack.getDependencies());
            return pack.getId();
        } finally {
            if (pack != null && pack.isClosed()) {
                pack.close();
            }
        }
    }

    @Nonnull
    @Override
    public PackageId registerExternal(@Nonnull File file, boolean replace) throws IOException, PackageExistsException {
        ZipVaultPackage pack = new ZipVaultPackage(file, false, true);
        try {
//            xyz .. missing tests for external 
            setInstallState(pack.getId(), FSPackageStatus.REGISTERED, file.getPath(), true, pack.getDependencies());
            return pack.getId();
        } finally {
            if (pack != null && pack.isClosed()) {
                pack.close();
            }
        }
    }

    @Override
    public void remove(@Nonnull PackageId id) throws IOException, NoSuchPackageException {
        InstallState state = getInstallState(id);
        File metaData = getPackageMetaDataFile(id);
        
        if (!metaData.exists()) {
            throw new NoSuchPackageException().setId(id);
        }
        metaData.delete();
        
        if(!state.isExternal()) {
            getPackageFile(id).delete();
        }
        dispatch(PackageEvent.Type.REMOVE, id, null);
    }

    @Nonnull
    @Override
    public Set<PackageId> packages() throws IOException {
        Set<PackageId> packageIds = packagesInitializied ? stateCache.keySet() : loadPackageCache();
        return packageIds;
    }

    private Set<PackageId> loadPackageCache() throws IOException {
        Map<PackageId, InstallState> cacheEntries = new HashMap<>();
        
        Collection<File> files = FileUtils.listFiles(getHomeDir(), META_SUFFIXES, true);
        for (File file : files) {
            InstallState state = getInstallState(file);
            PackageId id = state.getPackageId();
            if (id != null) {
                cacheEntries.put(id, state);
            }
        }
        stateCache.putAll(cacheEntries);
        packagesInitializied = true;
        return cacheEntries.keySet();
    }

    /**
     * Returns the path of this package.this also includes the version, but
     * never the extension (.zip).
     *
     * @param id the package id
     * @return the path of this package
     */
    public String getInstallationPath(PackageId id) {
        return getRelativeInstallationPath(id);
    }

    @Override
    public void installPackage(Session session, RegisteredPackage pkg, ImportOptions opts, boolean extract) throws IOException, PackageException {

        if (session == null){
            String msg = "Installation of packages only supported when session is set.";
            log.error(msg);
            throw new PackageException(msg);
        }
        
        // For now FS based persistence only supports extraction but no reversible installation
        if (!extract) {
            String msg = "Only extraction supported by FS based registry";
            log.error(msg);
            throw new PackageException(msg);
        }
        VaultPackage vltPkg = pkg.getPackage();
        if (vltPkg instanceof ZipVaultPackage) {
            try {
                ((ZipVaultPackage)vltPkg).extract(session, opts);
            } catch (RepositoryException e) {
                throw new IOException(e);
            }
            FSPackageStatus targetStatus = extract ? FSPackageStatus.EXTRACTED : FSPackageStatus.INSTALLED;
            updateInstallState(vltPkg.getId(), targetStatus);
        }
    }

    @Override
    public void uninstallPackage(Session session, RegisteredPackage pkg, ImportOptions opts) throws IOException, PackageException {
        String msg = "Uninstallation not supported by FS based registry";
        log.error(msg);
        throw new PackageException(msg);
    }
    
    private void updateInstallState(PackageId pid, FSPackageStatus targetStatus) throws IOException {
        InstallState state = getInstallState(pid);
        setInstallState(pid, targetStatus, state.getFilePath(), state.isExternal(), state.getDependencies());
    }

    private void setInstallState(PackageId pid, FSPackageStatus targetStatus, String filePath, boolean external, Dependency[] dependencies) throws IOException {
        File metaData = getPackageMetaDataFile(pid);
        
        if (targetStatus.equals(FSPackageStatus.NOTREGISTERED)) {
            metaData.delete();
            stateCache.remove(pid);
        } else {
            OutputStream out = FileUtils.openOutputStream(metaData);
            try {
                XMLSerializer ser = new XMLSerializer(out, new OutputFormat("xml", "UTF-8", true));
                ser.startDocument();
                AttributesImpl attrs = new AttributesImpl();
                attrs.addAttribute(null, null, ATTR_PACKAGE_ID, "CDATA", pid.toString());
                attrs.addAttribute(null, null, ATTR_FILE_PATH, "CDATA", filePath);
                attrs.addAttribute(null, null, ATTR_EXTERNAL, "CDATA", Boolean.toString(external));
                attrs.addAttribute(null, null, ATTR_PACKAGE_STATUS, "CDATA", targetStatus.name().toLowerCase());
                ser.startElement(null, null, TAG_REGISTRY_METADATA, attrs);
                if (dependencies != null) {
                    for (Dependency dependency : dependencies) {
                        attrs = new AttributesImpl();
                        attrs.addAttribute(null, null, ATTR_PACKAGE_ID, "CDATA", dependency.toString());
                        ser.startElement(null, null, TAG_DEPENDENCY, attrs);
                        ser.endElement(TAG_DEPENDENCY);
                    }
                }
                ser.endElement(TAG_REGISTRY_METADATA);
                ser.endDocument();
            } catch (SAXException e) {
                throw new IllegalStateException(e);
            } finally {
                IOUtils.closeQuietly(out);
            }
            stateCache.put(pid, getInstallState(metaData));
        }
    }
    
    public InstallState getInstallState(PackageId pid) throws IOException {
        if(stateCache.containsKey(pid)) {
            return stateCache.get(pid);
        } else {
            File metaFile = getPackageMetaDataFile(pid);
            InstallState state = getInstallState(metaFile);
            if (state != null) {
                stateCache.put(pid, state);
            }
            return state != null ? state : new InstallState(pid, FSPackageStatus.NOTREGISTERED, null, false, null);
        }
    }



    private InstallState getInstallState(File metaFile) throws IOException {
        if (metaFile.exists()) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                builder.setEntityResolver(new RejectingEntityResolver());
                Document document = builder.parse(metaFile);
                Element doc = document.getDocumentElement();
                if (!TAG_REGISTRY_METADATA.equals(doc.getNodeName())) {
                    throw new IOException("<" + TAG_REGISTRY_METADATA + "> expected.");
                }
                String packageId = doc.getAttribute(ATTR_PACKAGE_ID);
                String filePath = doc.getAttribute(ATTR_FILE_PATH);
                boolean external = Boolean.parseBoolean(doc.getAttribute(ATTR_EXTERNAL));
                FSPackageStatus status = FSPackageStatus.valueOf(doc.getAttribute(ATTR_PACKAGE_STATUS).toUpperCase());
                NodeList nl = doc.getChildNodes();
                List<Dependency> dependencies = new ArrayList<>();
                for (int i=0; i<nl.getLength(); i++) {
                    Node child = nl.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        if (!TAG_DEPENDENCY.equals(child.getNodeName())) {
                            throw new IOException("<" + TAG_DEPENDENCY + "> expected.");
                        }
                        dependencies.add(readDependency((Element) child));
                    }
                }
                return new InstallState(PackageId.fromString(packageId), status, filePath, external, dependencies.toArray(new Dependency[dependencies.size()]));
            } catch (ParserConfigurationException e) {
                throw new IOException("Unable to create configuration XML parser", e);
            } catch (SAXException e) {
                throw new IOException("Configuration file syntax error.", e);
            }
        }
        return null;
    }

    private Dependency readDependency(Element child) {
        return Dependency.fromString(child.getAttribute(ATTR_PACKAGE_ID));
    }
}

    class InstallState{

        private PackageId packageId;
        private FSPackageStatus status;
        private String filePath;
        private boolean external;
        private Dependency[] dependencies;

        public InstallState(@Nonnull PackageId packageId, @Nonnull FSPackageStatus status, @Nullable String filePath, boolean external,@Nullable Dependency[] dependencies) {
            this.packageId = packageId;
            this.status = status;
            this.filePath = filePath;
            this.external = external;
            this.dependencies = dependencies;
        }

        public PackageId getPackageId() {
            return packageId;
        }

        public String getFilePath() {
            return filePath;
        }

        public boolean isExternal() {
            return external;
        }

        public FSPackageStatus getStatus() {
            return status;
        }

        public Dependency[] getDependencies() {
            return dependencies;
        }
    }
