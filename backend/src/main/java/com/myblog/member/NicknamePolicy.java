package com.myblog.member;

import com.myblog.common.MyBlogProperties;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.Messages;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** CF-01-3: 한글·영문·숫자 2~10자, 영문 대소문자는 같은 것으로 본다. */
@Component
public class NicknamePolicy {
    private static final Pattern ALLOWED = Pattern.compile("^[가-힣A-Za-z0-9]+$");
    private final MyBlogProperties.Member rules;

    public NicknamePolicy(MyBlogProperties props) {
        this.rules = props.member();
    }

    public String validate(String raw) {
        String nickname = raw == null ? "" : raw.strip();
        int len = nickname.codePointCount(0, nickname.length());
        if (len < rules.nicknameMin() || len > rules.nicknameMax() || !ALLOWED.matcher(nickname).matches()) {
            throw ApiException.field("nickname", Messages.NICKNAME_RULE);
        }
        return nickname;
    }

    public static String key(String nickname) {
        return nickname.toLowerCase(Locale.ROOT);
    }
}
