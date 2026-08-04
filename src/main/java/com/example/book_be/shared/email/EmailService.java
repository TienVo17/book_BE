package com.example.book_be.shared.email;


public interface EmailService {
    void ensureConfigured();

    void sendEmail(String to, String subject, String text);
}
