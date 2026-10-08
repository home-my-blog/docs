package com.myblog.manage;

import com.myblog.blog.BlogService;
import com.myblog.common.PageResponse;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 블로그 관리 (BM-01-2: 로그인한 블로그 주인만, 다른 사람의 블로그는 주소로도 못 본다). */
@RestController
public class ManageController {
    private final ManageService manage;
    private final BlogService blogs;

    public ManageController(ManageService manage, BlogService blogs) {
        this.manage = manage;
        this.blogs = blogs;
    }

    @GetMapping("/api/manage/dashboard")
    public Map<String, Object> dashboard(@AuthenticationPrincipal Long me) {
        return manage.dashboard(blogs.requireOwnBlogId(me), me);
    }

    @GetMapping("/api/manage/posts")
    public PageResponse<Map<String, Object>> posts(@AuthenticationPrincipal Long me,
                                                   @RequestParam(required = false) String visibility,
                                                   @RequestParam(required = false) Long categoryId,
                                                   @RequestParam(required = false) Integer page) {
        return manage.posts(blogs.requireOwnBlogId(me), visibility, categoryId, page);
    }

    @GetMapping("/api/manage/comments")
    public PageResponse<Map<String, Object>> comments(@AuthenticationPrincipal Long me,
                                                      @RequestParam(required = false) Integer page) {
        return manage.comments(blogs.requireOwnBlogId(me), me, page);
    }

    @GetMapping("/api/manage/stats")
    public Map<String, Object> stats(@AuthenticationPrincipal Long me,
                                     @RequestParam(defaultValue = "30") int days) {
        int d = days == 7 ? 7 : 30;
        return Map.of("days", d, "daily", manage.daily(blogs.requireOwnBlogId(me), d));
    }
}
