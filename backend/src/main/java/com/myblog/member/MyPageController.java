package com.myblog.member;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** contracts/api.md '마이페이지'. SecurityConfig가 /api/me/** 를 로그인 필요로 막는다. */
@RestController
public class MyPageController {
    private final MyPageService service;

    public MyPageController(MyPageService service) {
        this.service = service;
    }

    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal Long me) {
        return service.view(me);
    }

    @PatchMapping("/api/me")
    public ResponseEntity<Void> update(@AuthenticationPrincipal Long me, @RequestBody MyPageService.ProfileUpdate req) {
        service.updateProfile(me, req);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/api/me/password")
    public ResponseEntity<Void> password(@AuthenticationPrincipal Long me, HttpServletRequest http,
                                         @RequestBody MyPageService.PasswordChange req) {
        HttpSession session = http.getSession(false);
        service.changePassword(me, session == null ? null : session.getId(), req);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal Long me, HttpServletRequest http,
                                         @RequestBody MyPageService.Withdraw req) {
        service.withdraw(me, req);
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
