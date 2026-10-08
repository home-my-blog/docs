package com.myblog.mail;

/** 메일 발송 (research §4). 구현은 myblog.mail.mode로 고른다: log(개발) / smtp. */
public interface MailSender {

    /** 메일을 보낼 수 있는 상태인지 바로 확인한다. 실패하면 MAIL_SEND_FAILED (CF-01-18). */
    boolean isAvailable();

    /** 비동기로 보낸다. */
    void send(String to, String subject, String body);
}
