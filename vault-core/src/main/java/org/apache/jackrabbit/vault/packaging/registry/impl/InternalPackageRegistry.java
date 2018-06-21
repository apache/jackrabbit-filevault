package org.apache.jackrabbit.vault.packaging.registry.impl;

import java.io.IOException;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.registry.PackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;

public interface InternalPackageRegistry extends PackageRegistry {
    
    void installPackage(RegisteredPackage pkg, ImportOptions opts, boolean extract)
            throws IOException, PackageException;
    void uninstallPackage(RegisteredPackage pkg, ImportOptions opts) throws IOException, PackageException ;

}
