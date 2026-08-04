package com.example.book_be.shared.email;

import com.example.book_be.donhang.domain.ChiTietDonHang;
import com.example.book_be.donhang.web.DonHangController;
import com.example.book_be.sach.domain.Sach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmailHtmlEncodingTest {

    @Test
    void order_email_encodes_every_dynamic_html_value() {
        Sach sach = new Sach();
        sach.setTenSach("<img src=x onerror=alert(1)>");
        sach.setGiaBan(100D);
        ChiTietDonHang chiTiet = new ChiTietDonHang();
        chiTiet.setSach(sach);
        chiTiet.setSoLuong(1);

        String body = new DonHangController().generateOrderEmailBody(
                "<script>order</script>",
                "Khách <b>xấu</b>",
                "<svg onload=alert(1)>",
                "<a href=javascript:alert(1)>địa chỉ</a>",
                "<iframe>100</iframe>",
                List.of(chiTiet));

        assertThat(body)
                .doesNotContain("<script>order</script>")
                .doesNotContain("<img src=x onerror=alert(1)>")
                .doesNotContain("<svg onload=alert(1)>")
                .doesNotContain("<a href=javascript:")
                .contains("&lt;a href=javascript:alert(1)&gt;")
                .contains("&lt;script&gt;order&lt;/script&gt;")
                .contains("&lt;img src=x onerror=alert(1)&gt;");
    }
}
