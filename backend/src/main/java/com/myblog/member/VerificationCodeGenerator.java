package com.myblog.member;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** CF-01-12: 영문 대문자+숫자 6자리, 헷갈리는 O·0·I·1 제외. 추측할 수 없는 무작위 값 (NF-05). */
@Component
public class VerificationCodeGenerator {
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
