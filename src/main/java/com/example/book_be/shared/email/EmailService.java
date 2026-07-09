package com.example.book_be.shared.email;


public interface EmailService {
    public void sendEmail(String from, String to, String subject, String text);
}
