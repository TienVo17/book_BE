package com.example.book_be.shared.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceImplTest {

    @Test
    void uses_only_the_runtime_configured_sender() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        EmailServiceImpl service = new EmailServiceImpl(sender, "no-reply@example.test");

        service.sendEmail("recipient@example.test", "Subject", "<p>Safe</p>");

        assertThat(message.getFrom()).extracting(Object::toString)
                .containsExactly("no-reply@example.test");
        verify(sender).send(message);
    }

    @Test
    void fails_closed_when_mail_from_is_absent() {
        EmailServiceImpl service = new EmailServiceImpl(mock(JavaMailSender.class), " ");

        assertThatThrownBy(service::ensureConfigured)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_FROM");
        assertThatThrownBy(() -> service.sendEmail("recipient@example.test", "Subject", "Body"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_FROM");
    }
}
