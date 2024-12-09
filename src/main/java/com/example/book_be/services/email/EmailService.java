package com.example.book_be.services.email;


public interface EmailService {
    public void sendEmail(String from, String to, String subject, String text);
}
