package com.example.book_be.shared.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {
    private final JavaMailSender mailSender;
    private final String fromAddress;

    @Autowired
    public EmailServiceImpl(JavaMailSender mailSender, @Value("${app.mail.from:}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void ensureConfigured() {
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException("MAIL_FROM chưa được cấu hình.");
        }
    }

    @Override
    public void sendEmail(String to, String subject, String text) {
        ensureConfigured();
        // MimeMailMessage => có đính kèm media
        // SimpleMailMessage => nội dung thông thường
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text,true);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
        // thực hiện hành động gửi email
        mailSender.send(message);

    }
}
