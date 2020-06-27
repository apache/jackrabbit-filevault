package org.apache.jackrabbit.vault.rcp.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.util.Text;
import org.apache.sling.jcr.api.SlingRepository;

public class FileNodeBackedOutputStream extends OutputStream {

    private final Session session;
    private final ByteArrayOutputStream output;
    private final String nodePath;
    private final String mimeType;

    public FileNodeBackedOutputStream(SlingRepository repository, String nodePath, String mimeType) throws RepositoryException {
        session = repository.loginService(null, null);
        output = new ByteArrayOutputStream();
        this.nodePath = nodePath;
        this.mimeType = mimeType;
    }

    @Override
    public void close() throws IOException {
        output.close();
        byte[] data = output.toByteArray();
        String parentNodePath = Text.getRelativeParent(nodePath, 1);
        try {
            Node parentNode = JcrUtils.getOrCreateByPath(parentNodePath, JcrConstants.NT_FOLDER, session);
            try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
                JcrUtils.putFile(parentNode, Text.getName(nodePath), mimeType, in);
            }
            session.logout();
        } catch (RepositoryException e) {
            throw new IOException("Could not persist in repository", e);
        }
    }

    @Override
    public void write(int b) throws IOException {
        output.write(b);
    }

}
