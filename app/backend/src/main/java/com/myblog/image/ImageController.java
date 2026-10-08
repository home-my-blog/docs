package com.myblog.image;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ImageController {
    private final ImageService images;

    public ImageController(ImageService images) {
        this.images = images;
    }

    @PostMapping("/api/images")
    public ResponseEntity<ImageService.Uploaded> upload(@AuthenticationPrincipal Long me,
                                                        @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(201).body(images.upload(me, file));
    }

    /** 저장소에서 읽어 내려 준다 (MinIO 포트를 밖에 열지 않는다). */
    @GetMapping("/api/images/**")
    public ResponseEntity<byte[]> get(HttpServletRequest req) {
        String key = req.getRequestURI().substring("/api/images/".length());
        var image = images.read(key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }
}
