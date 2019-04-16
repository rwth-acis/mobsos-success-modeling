package i5.las2peer.services.mobsos.successModeling.files;

import java.util.List;

public interface FileBackend {

    public String getFile(String path) throws FileBackendException;
    public List<String> listFiles(String path) throws FileBackendException;
    public List<String> listFiles() throws FileBackendException;
}
