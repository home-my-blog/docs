package com.myblog.post;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 내 임시저장 글 (CF-05). 로그인한 본인 것만. */
@RestController
public class DraftController {
    private final DraftService drafts;

    public DraftController(DraftService drafts) {
        this.drafts = drafts;
    }

    @GetMapping("/api/me/drafts")
    public Map<String, Object> list(@AuthenticationPrincipal Long me) {
        return drafts.list(me);
    }

    @GetMapping("/api/me/drafts/{id}")
    public Map<String, Object> get(@PathVariable long id, @AuthenticationPrincipal Long me) {
        return drafts.get(me, id);
    }

    @PostMapping("/api/me/drafts")
    public ResponseEntity<Map<String, Object>> create(@AuthenticationPrincipal Long me,
                                                      @RequestBody DraftService.Request req) {
        return ResponseEntity.status(201).body(drafts.save(me, null, req));
    }

    @PutMapping("/api/me/drafts/{id}")
    public Map<String, Object> update(@PathVariable long id, @AuthenticationPrincipal Long me,
                                      @RequestBody DraftService.Request req) {
        return drafts.save(me, id, req);
    }

    @DeleteMapping("/api/me/drafts/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, @AuthenticationPrincipal Long me) {
        drafts.delete(me, id);
        return ResponseEntity.noContent().build();
    }
}
