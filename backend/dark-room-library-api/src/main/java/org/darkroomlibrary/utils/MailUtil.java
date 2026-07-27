package org.darkroomlibrary.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 邮件发送工具类
 */
@Slf4j
@Component
public class MailUtil {

    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Value("${spring.mail.password:}")
    private String password;

    /**
     * 发送验证码
     */
    public void sendVerificationCode(String to, String code) {
        ensureMailConfigured();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("【暗室藏书】邮箱验证码");
        message.setText("您的验证码是：" + code + "，有效期为5分钟，请勿泄露给他人。");
        // 验证码发送失败必须抛出异常，调用方不应给用户"已发送"的假提示
        mailSender.send(message);
        log.info("验证码邮件发送成功: to={}", to);
    }

    public void sendSimpleOrThrow(String to, String subject, String content) {
        ensureMailConfigured();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
        log.info("通知邮件发送成功: to={}, subject={}", to, subject);
    }

    private void ensureMailConfigured() {
        if (!isMailConfigured()) {
            throw new IllegalStateException("邮件服务未配置：请设置 MAIL_USERNAME 和 MAIL_PASSWORD");
        }
    }

    private boolean isMailConfigured() {
        return from != null && !from.trim().isEmpty()
                && password != null && !password.trim().isEmpty();
    }
}
