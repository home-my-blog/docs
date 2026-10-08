package com.myblog.member;

import com.myblog.blog.BlogService;
import com.myblog.common.KoreanClock;
import com.myblog.common.MyBlogProperties;
import com.myblog.common.Texts;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import com.myblog.image.ImageService;
import com.myblog.mail.MailSender;
import com.myblog.mail.MailTemplates;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 마이페이지: 내 정보, 비밀번호 변경, 탈퇴 (CF-15). */
@Service
public class MyPageService {
    public record ProfileUpdate(String nickname, String bio) {}

    public record PasswordChange(String currentPassword, String newPassword, String newPasswordConfirm) {}

    public record Withdraw(String password, Boolean acknowledged) {}

    private final MemberRepository members;
    private final NicknamePolicy nicknames;
    private final PasswordPolicy passwords;
    private final LoginService login;
    private final PasswordEncoder encoder;
    private final MemberSessionService sessions;
    private final MailSender mail;
    private final MailTemplates templates;
    private final BlogService blogs;
    private final ImageService images;
    private final JdbcClient jdbc;
    private final MyBlogProperties props;
    private final KoreanClock clock;

    public MyPageService(MemberRepository members, NicknamePolicy nicknames, PasswordPolicy passwords,
                         LoginService login, PasswordEncoder encoder, MemberSessionService sessions, MailSender mail,
                         MailTemplates templates, BlogService blogs, ImageService images, JdbcClient jdbc,
                         MyBlogProperties props, KoreanClock clock) {
        this.members = members;
        this.nicknames = nicknames;
        this.passwords = passwords;
        this.login = login;
        this.encoder = encoder;
        this.sessions = sessions;
        this.mail = mail;
        this.templates = templates;
        this.blogs = blogs;
        this.images = images;
        this.jdbc = jdbc;
        this.props = props;
        this.clock = clock;
    }

    private Member member(long id) {
        return members.findById(id).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    public Map<String, Object> view(long memberId) {
        Member m = member(memberId);
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("email", m.email());
        v.put("nickname", m.nickname());
        v.put("bio", m.bio());
        v.put("createdAt", m.createdAt());
        v.put("blogId", blogs.blogIdOf(memberId).orElse(null));
        return v;
    }

    @Transactional
    public void updateProfile(long memberId, ProfileUpdate req) {
        Member m = member(memberId);
        String nickname = req.nickname() == null ? m.nickname() : nicknames.validate(req.nickname());
        String bio = req.bio() == null ? m.bio() : Texts.trim(req.bio());
        if (Texts.length(bio) > props.member().bioMax()) {
            throw ApiException.field("bio", Messages.BIO_RULE);
        }
        if (members.existsByNicknameKey(NicknamePolicy.key(nickname), memberId)) { // 내 닉네임은 중복 아님 (CF-15-5)
            throw ApiException.withDetails(ErrorCode.NICKNAME_TAKEN, Messages.NICKNAME_TAKEN,
                    Map.of("fields", Map.of("nickname", Messages.NICKNAME_TAKEN)));
        }
        members.updateProfile(memberId, nickname, NicknamePolicy.key(nickname), bio, clock.nowOffset());
    }

    /** 지금 기기는 유지하고 다른 기기의 로그인을 끊는다 (CF-15-15). */
    @Transactional
    public void changePassword(long memberId, String currentSessionId, PasswordChange req) {
        Member m = member(memberId);
        login.verifyPassword(m, req.currentPassword(), ErrorCode.CURRENT_PASSWORD_MISMATCH);
        passwords.validate("newPassword", req.newPassword(), "newPasswordConfirm", req.newPasswordConfirm());
        if (encoder.matches(req.newPassword(), m.passwordHash())) {
            throw ApiException.withDetails(ErrorCode.SAME_AS_CURRENT, Messages.SAME_AS_CURRENT,
                    Map.of("fields", Map.of("newPassword", Messages.SAME_AS_CURRENT)));
        }
        members.updatePassword(memberId, encoder.encode(req.newPassword()), clock.nowOffset());
        sessions.expireAll(memberId, currentSessionId);
        var t = templates.passwordChanged();
        mail.send(m.email(), t.subject(), t.body()); // CF-15-16
    }

    /**
     * 탈퇴 (CF-15-17~21, research §11). 내 블로그·글·분류와 그 글의 댓글·좋아요·태그 연결·이미지, 내가 누른 좋아요,
     * 내 신고를 지운다. 남의 글에 단 댓글은 작성자 칸이 비워져 "탈퇴한 사용자"로 남는다(FK ON DELETE SET NULL).
     */
    @Transactional
    public void withdraw(long memberId, Withdraw req) {
        Member m = member(memberId);
        if (!Boolean.TRUE.equals(req.acknowledged())) {
            throw new ApiException(ErrorCode.ACKNOWLEDGE_REQUIRED);
        }
        login.verifyPassword(m, req.password(), ErrorCode.CURRENT_PASSWORD_MISMATCH);
        List<String> keys = jdbc.sql("""
                SELECT storage_key FROM post_images
                WHERE uploader_id = ? OR post_id IN (SELECT id FROM posts WHERE author_id = ?)""")
                .params(memberId, memberId).query(String.class).list();
        jdbc.sql("DELETE FROM posts WHERE author_id = ?").param(memberId).update(); // 분류 RESTRICT보다 먼저
        jdbc.sql("DELETE FROM blogs WHERE owner_id = ?").param(memberId).update();
        members.delete(memberId);
        images.deleteFilesAfterCommit(keys);
        sessions.expireAll(memberId, null);
    }
}
