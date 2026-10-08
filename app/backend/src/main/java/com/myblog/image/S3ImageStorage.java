package com.myblog.image;

import com.myblog.common.MyBlogProperties;
import java.net.URI;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** MinIO(S3 방식)에 path-style로 연결한다 (04-MinIO 권고). */
@Component
@ConditionalOnProperty(name = "myblog.image.storage", havingValue = "s3")
public class S3ImageStorage implements ImageStorage {
    private final S3Client s3;
    private final String bucket;

    public S3ImageStorage(MyBlogProperties props) {
        var c = props.image().s3();
        this.bucket = c.bucket();
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(c.endpoint()))
                .region(Region.of(c.region()))
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(c.accessKey(), c.secretKey())))
                .build();
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType), RequestBody.fromBytes(bytes));
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(b -> b.bucket(bucket).key(key));
    }
}
