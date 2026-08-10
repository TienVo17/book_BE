package com.example.book_be.shared.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MailjetEmailServiceTest {

    private HttpClient httpClient;
    private MailjetEmailService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        service = new MailjetEmailService(httpClient, "khoa-api", "khoa-bi-mat", "shop@example.com");
    }

    @SuppressWarnings("unchecked")
    private void traLoi(int maTrangThai) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(maTrangThai);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    @Test
    void gui_dung_dia_chi_tieu_de_va_noi_dung() throws Exception {
        traLoi(200);

        service.sendEmail("khach@example.com", "Xác nhận đăng ký", "<p>Xin chào</p>");

        ArgumentCaptor<HttpRequest> bat = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(bat.capture(), any());
        assertThat(bat.getValue().uri().toString()).isEqualTo("https://api.mailjet.com/v3.1/send");
        assertThat(bat.getValue().method()).isEqualTo("POST");
    }

    @Test
    void xac_thuc_bang_basic_auth_tu_cap_khoa() throws Exception {
        traLoi(200);

        service.sendEmail("khach@example.com", "Tiêu đề", "<p>Nội dung</p>");

        ArgumentCaptor<HttpRequest> bat = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(bat.capture(), any());
        String mongDoi = "Basic " + Base64.getEncoder()
                .encodeToString("khoa-api:khoa-bi-mat".getBytes(StandardCharsets.UTF_8));
        assertThat(bat.getValue().headers().firstValue("Authorization")).contains(mongDoi);
    }

    /**
     * Noi chuoi bang tay se sinh JSON hong ngay khi tieu de chua dau nhay kep hoac xuong dong —
     * ma noi dung o day luon la tieng Viet va HTML.
     */
    @Test
    void than_json_hop_le_khi_noi_dung_co_dau_nhay_va_xuong_dong() throws Exception {
        String than = service.thanRequest(
                "khach@example.com",
                "Sách \"Đắc Nhân Tâm\" giảm giá",
                "<p>Dòng một</p>\n<p>Dòng \"hai\"</p>");

        JsonNode goc = objectMapper.readTree(than);
        JsonNode thu = goc.get("Messages").get(0);
        assertThat(thu.get("From").get("Email").asText()).isEqualTo("shop@example.com");
        assertThat(thu.get("To").get(0).get("Email").asText()).isEqualTo("khach@example.com");
        assertThat(thu.get("Subject").asText()).isEqualTo("Sách \"Đắc Nhân Tâm\" giảm giá");
        assertThat(thu.get("HTMLPart").asText()).contains("Dòng \"hai\"");
    }

    @Test
    void mailjet_tu_choi_thi_nem_loi_chu_khong_im_lang() throws Exception {
        traLoi(401);

        assertThatThrownBy(() -> service.sendEmail("khach@example.com", "T", "<p>N</p>"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("401");
    }

    @Test
    void loi_mang_thi_nem_loi() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("mat ket noi"));

        assertThatThrownBy(() -> service.sendEmail("khach@example.com", "T", "<p>N</p>"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Không gọi được Mailjet");
    }

    @Test
    void thieu_khoa_thi_bao_ngay_chu_khong_goi_mang() {
        MailjetEmailService thieuKhoa =
                new MailjetEmailService(httpClient, "", "", "shop@example.com");

        assertThatThrownBy(() -> thieuKhoa.sendEmail("a@example.com", "T", "<p>N</p>"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAILJET_API_KEY");
        verifyNoInteractions(httpClient);
    }

    @Test
    void thieu_dia_chi_gui_thi_bao_ngay() {
        MailjetEmailService thieuFrom =
                new MailjetEmailService(httpClient, "khoa-api", "khoa-bi-mat", "");

        assertThatThrownBy(() -> thieuFrom.sendEmail("a@example.com", "T", "<p>N</p>"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_FROM");
        verifyNoInteractions(httpClient);
    }
}
