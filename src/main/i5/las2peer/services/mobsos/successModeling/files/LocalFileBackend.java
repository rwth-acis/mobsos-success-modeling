package i5.las2peer.services.mobsos.successModeling.files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class LocalFileBackend implements FileBackend {

    private String basePath;

    public LocalFileBackend(String basePath) {
        this.basePath = basePath;
    }

    @Override
    public String getFile(String path) throws FileBackendException {
        Path realPath = Paths.get(basePath, path);
        try {
            return Files.lines(realPath, Charset.defaultCharset()).collect(Collectors.joining());
        } catch (IOException e) {
            throw new FileBackendException(e);
        }
    }

    @Override
    public List<String> listFiles() throws FileBackendException {
        return listFiles("");
    }

    public List<String> listFiles(String path) throws FileBackendException {
        Path realPath = Paths.get(basePath, path);
        try {
            return Files.walk(realPath)
                    .filter(Files::isRegularFile).map(java.nio.file.Path::toFile).map(File::getName)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new FileBackendException(e);
        }
    }
}
