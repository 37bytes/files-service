package dev.b37.libs.fs;

public class FileServiceException extends RuntimeException {
    public FileServiceException() {
        super();
    }

    public FileServiceException(String message) {
        super(message);
    }

    public FileServiceException(String message, Throwable cause) {
        super(message, cause);
    }


}
