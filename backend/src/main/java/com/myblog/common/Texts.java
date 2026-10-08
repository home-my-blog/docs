package com.myblog.common;

/** 입력 문자열 공통 처리. */
public final class Texts {
    private Texts() {}

    /** 앞뒤 공백을 지운다. null은 빈 문자열로 본다. */
    public static String trim(String s) {
        return s == null ? "" : s.strip();
    }

    /** 글자 수는 코드 포인트로 센다(한글·이모지 한 글자 = 1). */
    public static int length(String s) {
        return s == null ? 0 : s.codePointCount(0, s.length());
    }

    public static String cut(String s, int max) {
        if (length(s) <= max) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, max)) + "…";
    }
}
