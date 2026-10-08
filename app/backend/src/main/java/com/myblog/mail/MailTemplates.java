package com.myblog.mail;

import com.myblog.common.MyBlogProperties;
import org.springframework.stereotype.Component;

/** CF-01-12, CF-25-4, CF-15-16의 메일 내용. */
@Component
public class MailTemplates {
    public record Mail(String subject, String body) {}

    private final String service;
    private final long minutes;

    public MailTemplates(MyBlogProperties props) {
        this.service = props.mail().serviceName();
        this.minutes = props.verification().codeTtl().toMinutes();
    }

    public Mail signupCode(String code) {
        return new Mail("[" + service + "] 회원가입 인증번호", """
                %s 회원가입 인증번호입니다.

                인증번호: %s
                유효 시간: %d분

                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(service, code, minutes));
    }

    public Mail resetCode(String code) {
        return new Mail("[" + service + "] 비밀번호 찾기 인증번호", """
                %s 비밀번호 찾기 인증번호입니다.

                인증번호: %s
                유효 시간: %d분

                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(service, code, minutes));
    }

    public Mail passwordChanged() {
        return new Mail("[" + service + "] 비밀번호가 변경되었습니다", """
                %s 계정의 비밀번호가 변경되었습니다.

                본인이 변경하지 않았다면 비밀번호 찾기로 비밀번호를 다시 정해 주세요.
                """.formatted(service));
    }
}
