package org.apache.jackrabbit.vault.packaging.registry.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.registry.DependencyReport;
import org.apache.jackrabbit.vault.packaging.registry.ExecutionPlanBuilder;
import org.apache.jackrabbit.vault.packaging.registry.PackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;

public abstract class AbstractPackageRegistry implements PackageRegistry, InternalPackageRegistry {

    @Override
    public abstract void installPackage(Session session, RegisteredPackage pkg, ImportOptions opts, boolean extract)
            throws IOException, PackageException;

    @Override
    public abstract void uninstallPackage(Session session, RegisteredPackage pkg, ImportOptions opts)
            throws IOException, PackageException;

    @Override
    public abstract boolean contains(PackageId id) throws IOException;

    @Override
    public abstract Set<PackageId> packages() throws IOException;

    @Override
    public abstract RegisteredPackage open(PackageId id) throws IOException;

    @Override
    public abstract PackageId register(InputStream in, boolean replace) throws IOException, PackageExistsException;

    @Override
    public abstract PackageId register(File file, boolean replace) throws IOException, PackageExistsException;

    @Override
    public abstract PackageId registerExternal(File file, boolean replace) throws IOException, PackageExistsException;

    @Override
    public abstract void remove(PackageId id) throws IOException, NoSuchPackageException;

    @Nonnull
    @Override
    public DependencyReport analyzeDependencies(@Nonnull PackageId id, boolean onlyInstalled) throws IOException, NoSuchPackageException {
        List<Dependency> unresolved = new LinkedList<Dependency>();
        List<PackageId> resolved = new LinkedList<PackageId>();
        try (RegisteredPackage pkg = open(id)) {
            if (pkg == null) {
                throw new NoSuchPackageException().setId(id);
            }
            //noinspection resource
            for (Dependency dep : pkg.getPackage().getDependencies()) {
                PackageId resolvedId = resolve(dep, onlyInstalled);
                if (resolvedId == null) {
                    unresolved.add(dep);
                } else {
                    resolved.add(resolvedId);
                }
            }
        }

        return new DependencyReportImpl(id, unresolved.toArray(new Dependency[unresolved.size()]),
                resolved.toArray(new PackageId[resolved.size()])
        );
    }

    @Override
    public abstract PackageId resolve(Dependency dependency, boolean onlyInstalled) throws IOException;

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public PackageId[] usage(PackageId id) throws IOException {
        TreeSet<PackageId> usages = new TreeSet<PackageId>();
        for (PackageId pid : packages()) {
            try (RegisteredPackage pkg = open(pid)) {
                if (pkg == null || !pkg.isInstalled()) {
                    continue;
                }
                // noinspection resource
                for (Dependency dep : pkg.getPackage().getDependencies()) {
                    if (dep.matches(id)) {
                        usages.add(pid);
                        break;
                    }
                }
            }
        }
        return usages.toArray(new PackageId[usages.size()]);
    }
    
    

    @Nonnull
    @Override
    public ExecutionPlanBuilder createExecutionPlan() {
        return new ExecutionPlanBuilderImpl(this);
    }

    /**
     * Returns the relative path of this package. please note that since 2.3 this also
     * includes the version, but never the extension (.zip).
     *
     * @param id the package id
     * @return the relative path of this package
     * @since 2.2
     */
    public String getRelativeInstallationPath(PackageId id) {
        StringBuilder b = new StringBuilder("/");
        if (id.getGroup().length() > 0) {
            b.append(id.getGroup());
            b.append("/");
        }
        b.append(id.getName());
        String v = id.getVersion().toString();
        if (v.length() > 0) {
            b.append("-").append(v);
        }
        return b.toString();
    }


     /**
     * Creates a random package id for packages that lack one.
     * 
     * @return a random package id.
     */
    protected static PackageId createRandomPid() {
        return new PackageId("temporary", "pack_" + UUID.randomUUID().toString(), (String) null);
    }


}
