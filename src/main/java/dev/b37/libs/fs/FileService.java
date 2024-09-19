package dev.b37.libs.fs;

import java.nio.file.Path;
import java.util.List;

public interface FileService {

    void save(Path path, byte[] bytes) throws FileServiceException;

    void save(Path path, byte[] bytes, boolean overwrite) throws FileServiceException;

    void save(Path path, byte[] bytes, boolean overwrite, String contentType) throws FileServiceException;

    byte[] get(Path path) throws FileServiceException;

    void delete(Path path) throws FileServiceException;

    boolean exists(Path path) throws FileServiceException;

    List<String> list(Path path) throws FileServiceException;
}
