package com.myblog.image;

import java.util.Optional;

/** 이미지 저장소 (research §5). myblog.image.storage=disk|s3로 고른다. */
public interface ImageStorage {
    void put(String key, byte[] bytes, String contentType);

    Optional<byte[]> get(String key);

    void delete(String key);
}
