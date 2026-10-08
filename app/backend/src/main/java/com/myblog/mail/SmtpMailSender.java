package com.myblog.mail;

import com.myblog.common.MyBlogProperties;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "myblog.mail.mode", havingValue = "smtp")
public class SmtpMailSender implements MailSender {
    private static final Logger log = LoggerFactory.getLogger(SmtpMailSender.class);
    private final JavaMailSenderImpl mail;
    private final String from;

    public SmtpMailSender(JavaMailSenderImpl mail, MyBlogProperties props) {
        this.mail = mail;
        this.from = props.mail().from();
    }

    @Override
    public boolean isAvailable() {
        try {
            mail.testConnection();
            return true;
        } catch (MessagingException e) {
            log.warn("SMTP connection failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    @Async("mailExecutor")
    public void send(String to, String subject, String body) {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(to);
        m.setSubject(subject);
        m.setText(body);
        try {
            mail.send(m);
        } catch (RuntimeException e) {
            log.warn("Mail send failed to {}: {}", to, e.getMessage());
        }
    }
}
