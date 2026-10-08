package com.myblog.blog;

import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BlogController {
    public record NameRequest(String name) {}

    public record MoveRequest(String direction) {}

    private final BlogService blogs;
    private final CategoryService categories;

    public BlogController(BlogService blogs, CategoryService categories) {
        this.blogs = blogs;
        this.categories = categories;
    }

    @GetMapping("/api/blogs/{blogId}")
    public Map<String, Object> blog(@PathVariable long blogId, @AuthenticationPrincipal Long viewer) {
        return blogs.summary(blogs.get(blogId), viewer);
    }

    @GetMapping("/api/blogs/{blogId}/about")
    public Map<String, Object> about(@PathVariable long blogId) {
        var b = blogs.get(blogId);
        return Map.of("ownerNickname", b.ownerNickname(), "ownerBio", b.ownerBio(), "about", b.about());
    }

    @PatchMapping("/api/blogs/{blogId}")
    public ResponseEntity<Void> update(@PathVariable long blogId, @AuthenticationPrincipal Long me,
                                       @RequestBody BlogService.Update req) {
        blogs.update(blogId, me, req);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/blogs/{blogId}/categories")
    public List<CategoryService.CategoryView> categories(@PathVariable long blogId,
                                                         @AuthenticationPrincipal Long viewer) {
        var b = blogs.get(blogId);
        return categories.list(blogId, viewer != null && viewer == b.ownerId());
    }

    @PostMapping("/api/blogs/{blogId}/categories")
    public ResponseEntity<Map<String, Long>> add(@PathVariable long blogId, @AuthenticationPrincipal Long me,
                                                 @RequestBody NameRequest req) {
        blogs.requireOwner(blogId, me);
        return ResponseEntity.status(201).body(Map.of("id", categories.add(blogId, req.name())));
    }

    /** 다이어리 설정 (이름·소개·표지 색·공개 범위). 보낸 칸만 바꾼다. */
    @PatchMapping("/api/categories/{id}")
    public ResponseEntity<Void> update(@PathVariable long id, @AuthenticationPrincipal Long me,
                                       @RequestBody CategoryService.Update req) {
        categories.update(id, ownBlog(me), req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/categories/{id}/move")
    public ResponseEntity<Void> move(@PathVariable long id, @AuthenticationPrincipal Long me,
                                     @RequestBody MoveRequest req) {
        categories.move(id, ownBlog(me), req.direction());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/categories/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, @AuthenticationPrincipal Long me) {
        categories.delete(id, ownBlog(me));
        return ResponseEntity.noContent().build();
    }

    private long ownBlog(Long me) {
        if (me == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        return blogs.requireOwnBlogId(me);
    }
}
