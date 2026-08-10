package com.example.book_be.shared.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailHtmlEncodingTest {

    @Test
    void html_encoder_escapes_dynamic_email_values() {
        assertThat(HtmlEncoder.encode("<script>order</script>"))
                .isEqualTo("&lt;script&gt;order&lt;/script&gt;");
        assertThat(HtmlEncoder.encode("<a href=javascript:alert(1)>địa chỉ</a>"))
                .isEqualTo("&lt;a href=javascript:alert(1)&gt;địa chỉ&lt;/a&gt;");
    }
}
