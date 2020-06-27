package org.apache.jackrabbit.vault.rcp.impl;

import java.io.IOException;
import java.io.InputStream;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.jcr.api.SlingRepository;

public class FileNodeBackedInputStream extends InputStream {

    private final Session session;
    private final InputStream input;
    
    FileNodeBackedInputStream(SlingRepository repository, String nodePath) throws RepositoryException {
        session = repository.loginService(null, null);
        Node node = session.getNode(nodePath);
        input = JcrUtils.readFile(node);
    }

    @Override
    public int read() throws IOException {
        return input.read();
    }

    @Override
    public void close() throws IOException {
        super.close();
        session.logout();
    }

    
}
