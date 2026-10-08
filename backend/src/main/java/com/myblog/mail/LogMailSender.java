package com.myblog.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 개발용: 실제로 보내지 않고 서버 로그에 내용을 찍는다. */
@Component
@ConditionalOnProperty(name = "myblog.mail.mode", havingValue = "log", matchIfMissing = true)
public class LogMailSender implements MailSender {
    private static final Logger log = LoggerFactory.getLogger(LogMailSender.class);

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void send(String to, String subject, String body) {
        log.info("[메일] to={} subject={}\n{}", to, subject, body);
    }
}
