package com.myblog.image;

import com.myblog.common.MyBlogProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "myblog.image.storage", havingValue = "disk", matchIfMissing = true)
public class DiskImageStorage implements ImageStorage {
    private final Path root;

    public DiskImageStorage(MyBlogProperties props) {
        this.root = Path.of(props.image().diskPath()).toAbsolutePath().normalize();
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("bad key");
        }
        return p;
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        try {
            Path p = resolve(key);
            Files.createDirectories(p.getParent());
            Files.write(p, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            Path p = resolve(key);
            return Files.exists(p) ? Optional.of(Files.readAllBytes(p)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
