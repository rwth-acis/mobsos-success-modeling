package i5.las2peer.services.mobsos.successModeling.files;

public class FileBackendException extends Exception {

    public FileBackendException() {
    }

    public FileBackendException(String s) {
        super(s);
    }

    public FileBackendException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public FileBackendException(Throwable throwable) {
        super(throwable);
    }

    public FileBackendException(String s, Throwable throwable, boolean b, boolean b1) {
        super(s, throwable, b, b1);
    }
}
