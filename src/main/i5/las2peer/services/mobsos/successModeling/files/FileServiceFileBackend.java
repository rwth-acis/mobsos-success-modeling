package i5.las2peer.services.mobsos.successModeling.files;

import i5.las2peer.api.Context;
import i5.las2peer.api.execution.ServiceInvocationException;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FileServiceFileBackend implements FileBackend {
    private String basePath;
    private String fileServiceIdentifier;

    public FileServiceFileBackend(String basePath, String fileServiceIdentifier) {
        this.basePath = basePath;
        this.fileServiceIdentifier = fileServiceIdentifier;
    }

    @Override
    public String getFile(String path) throws FileBackendException {
        // normalize path to unix path
        String realPath = Paths.get(basePath, path).toString().replace('\\','/');
        Object result;
        try {
            result = invokeMethodOnFileService("fetchFile", realPath);
        } catch (ServiceInvocationException e) {
            throw new FileBackendException(e);
        }
        if (result != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) result;
            return new String((byte[]) response.get("content"));

        } else {
            throw new FileBackendException("Received null from file service fetchFile");
        }
    }

    @Override
    public List<String> listFiles(String path) throws FileBackendException {
        // normalize path to unix path
        String realPath = Paths.get(basePath, path).toString().replace('\\','/');
        Object result;
        try {
            result = invokeMethodOnFileService("getFileIndex");
        } catch (ServiceInvocationException e) {
            throw new FileBackendException(e);
        }
        if (result != null) {
            @SuppressWarnings("unchecked")
            ArrayList<Map<String, Object>> response = (ArrayList<Map<String, Object>>) result;
            // Filter results
            ArrayList<String> resultList = new ArrayList<>();
            for (Map<String, Object> k : response) {
                if (((String) k.get("identifier")).contains(realPath)) {
                    resultList.add((String) k.get("identifier"));
                }
            }
            return resultList;

        } else {
            throw new FileBackendException("Received null from file service getFileIndex");
        }
    }

    @Override
    public List<String> listFiles() throws FileBackendException {
        return listFiles("");
    }

    private Object invokeMethodOnFileService(String methodName, Object... args) throws ServiceInvocationException {
        return Context.get().invoke(fileServiceIdentifier, methodName, args);
    }
}
