package dev.b37.libs.fs;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.amazonaws.util.StringUtils;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class FileServiceS3 implements FileService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final long MB = 1024 * 1024;
    private static final String DELIMITER = "/";

    private final AmazonS3 s3client;
    private final TransferManager transferManager;
    private final String bucketName;

    public FileServiceS3(String accessKey, String secretKey, String bucketName, String url, String region) throws FileServiceException {
        boolean bucketExists = false;
        try {
            AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
            this.s3client = AmazonS3ClientBuilder
                    .standard()
                    .withPathStyleAccessEnabled(true)
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .withEndpointConfiguration(
                            new AmazonS3ClientBuilder.EndpointConfiguration(url, region)
                    )
                    .build();
            this.transferManager = TransferManagerBuilder.standard()
                    .withS3Client(s3client)
                    .withMultipartUploadThreshold(50 * MB)
                    .withExecutorFactory(() -> Executors.newFixedThreadPool(20))
                    .build();
            bucketExists = s3client.doesBucketExistV2(bucketName);
            log.debug("S3 client initialization successfully");
        } catch (Exception e) {
            throw new FileServiceException("Error initialization S3 client", e);
        }
        if (!bucketExists) {
            throw new FileServiceException(String.format("Bucket %s not exists", bucketName));
        }
        if (!StringUtils.isNullOrEmpty(bucketName)) {
            this.bucketName = bucketName;
        } else {
            throw new FileServiceException("Not set bucketName");
        }
    }

    @Override
    public void save(Path path, byte[] bytes) throws FileServiceException {
        save(path, bytes, false, null);
    }

    @Override
    public void save(Path path, byte[] bytes, boolean overwrite) throws FileServiceException {
        save(path, bytes, overwrite, null);
    }

    @Override
    public void save(Path path, byte[] bytes, boolean overwrite, String contentType) throws FileServiceException {
        log.debug("Start upload file {}, overwrite: {}", path, overwrite);
        if (!overwrite && exists(path)) {
            throw new FileServiceException(String.format("File/directory %s already exists, bucket %s", path, bucketName));
        }
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);

        if (contentType != null) {
            metadata.setContentType(contentType);
        }

        try (InputStream content = new ByteArrayInputStream(bytes)) {
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, path.toString(), content, metadata);
            Upload upload = transferManager.upload(putObjectRequest);
            upload.waitForUploadResult();
            log.debug("File {} uploaded", path);
        } catch (Exception e) {
           throw new FileServiceException(String.format("Error uploading file %s, bucket %s", path, bucketName), e);
        }
    }

    @Override
    public byte[] get(Path path) throws FileServiceException {
        log.debug("Get file {}", path);
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, path.toString());
        try (S3Object file = s3client.getObject(getObjectRequest)) {
            return file.getObjectContent().readAllBytes();
        } catch (Exception e) {
            throw new FileServiceException(String.format("Error downloading file %s, bucket %s", path, bucketName), e);
        }
    }

    /**
     * Удаляются только файлы (объекты), директорию (префикс) удалить нельзя, дл яэтого надо удалить все файлы внутри,
     * тогда префикс сам удалится
     */
    @Override
    public void delete(Path path) throws FileServiceException {
        try {
            log.debug("Delete file {}", path);
            DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName)
                    .withKeys(path.toString())
                    .withQuiet(true);
            s3client.deleteObjects(deleteObjectsRequest);
        } catch (Exception e) {
            throw new FileServiceException(String.format("Error deleting file %s, bucket %s", path, bucketName), e);
        }
    }

    /**
     * Чтобы файл (объект) не перезатер директорию (префикс) проверяется существования объекта, а после существование префикса (что он не пустой)
     */
    @Override
    public boolean exists(Path path) throws FileServiceException {
        try {
            log.debug("Check file/directory {} of exists", path);
            return s3client.doesObjectExist(bucketName, path.toString()) || !list(path).isEmpty();
        } catch (Exception e) {
            throw new FileServiceException(String.format("Error checking file/directory %s, bucket %s", path, bucketName), e);
        }
    }

    @Override
    public List<FileObject> list(Path path) throws FileServiceException {
        String prefix = path.toString().replaceFirst("^" + DELIMITER, "") + DELIMITER;
        log.debug("List directory {}, source path {}", prefix, path);
        try {
            ListObjectsV2Request listObjectsRequest = new ListObjectsV2Request();
            listObjectsRequest.setBucketName(bucketName);
            listObjectsRequest.setDelimiter(DELIMITER);
            listObjectsRequest.setPrefix(prefix);

            ListObjectsV2Result list = s3client.listObjectsV2(listObjectsRequest);
            List<FileObject> results = new ArrayList<>();
            list.getCommonPrefixes()
                    .forEach(p ->
                            {
                                String name = p.replaceFirst("^" + prefix, "");
                                results.add(new FileObject(FileObjectType.DIRECTORY, name.substring(0, name.length() - 1)));
                            }
                    );
            List<S3ObjectSummary> summaries = list.getObjectSummaries();
            for (S3ObjectSummary s : summaries) {
                results.add(new FileObject(FileObjectType.FILE, s.getKey().replaceFirst("^" + prefix, "")));
            }
            return results;
        } catch (Exception e) {
            throw new FileServiceException(String.format("Error listing %s, bucket %s", path, bucketName), e);
        }
    }
}
