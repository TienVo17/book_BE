package com.example.book_be.shared.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Gui thu qua API HTTP cua Mailjet thay vi SMTP.
 *
 * <p>Ly do ton tai: Render CHAN toan bo cong SMTP di ra (25, 465, 587) tren web service goi
 * mien phi tu 26/09/2025. Ket noi khong bao loi ngay ma treo cho toi khi het gio — mot request
 * dang ky nhan tin tren production mat 136 giay roi moi that bai. Vi ca thu kich hoat tai khoan
 * lan thu dat lai mat khau deu di qua cung mot duong, chan cong SMTP nghia la ca ba luong thu
 * cua he thong deu chet, khong rieng ban tin.
 *
 * <p>HTTP khong bi chan, va Mailjet dung dung cap API Key/Secret Key da cau hinh cho SMTP nen
 * khong can tai khoan hay khoa moi.
 */
@Service
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "mailjet")
public class MailjetEmailService implements EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailjetEmailService.class);
    private static final String DUONG_DAN = "https://api.mailjet.com/v3.1/send";
    private static final Duration HET_GIO = Duration.ofSeconds(20);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String secretKey;
    private final String fromAddress;

    // @Autowired la bat buoc: lop nay co hai constructor (mot cho Spring, mot cho test tiem
    // HttpClient gia). Khong danh dau thi Spring di tim constructor rong va boot that bai.
    @Autowired
    public MailjetEmailService(@Value("${app.mail.mailjet.api-key:}") String apiKey,
                               @Value("${app.mail.mailjet.secret-key:}") String secretKey,
                               @Value("${app.mail.from:}") String fromAddress) {
        this(HttpClient.newBuilder().connectTimeout(HET_GIO).build(), apiKey, secretKey, fromAddress);
    }

    /** Cho test tiem HttpClient gia; khong dung o runtime. */
    MailjetEmailService(HttpClient httpClient, String apiKey, String secretKey, String fromAddress) {
        this.httpClient = httpClient;
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.fromAddress = fromAddress;
        LOGGER.info("event=mail_provider provider=mailjet_http");
    }

    @Override
    public void ensureConfigured() {
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException("MAIL_FROM chưa được cấu hình.");
        }
        if (apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("MAILJET_API_KEY/MAILJET_SECRET_KEY chưa được cấu hình.");
        }
    }

    @Override
    public void sendEmail(String to, String subject, String text) {
        ensureConfigured();

        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(DUONG_DAN))
                    .timeout(HET_GIO)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                            (apiKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8)))
                    .POST(HttpRequest.BodyPublishers.ofString(thanRequest(to, subject, text), StandardCharsets.UTF_8))
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException bicatngang) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bị gián đoạn khi gửi thư.", bicatngang);
        } catch (Exception loi) {
            throw new IllegalStateException("Không gọi được Mailjet.", loi);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // Than phan hoi cua Mailjet co the chua dia chi nguoi nhan; chi ghi ma trang thai.
            LOGGER.warn("event=mail_send_failed provider=mailjet status={}", response.statusCode());
            throw new IllegalStateException("Mailjet từ chối yêu cầu gửi thư (HTTP " + response.statusCode() + ").");
        }
    }

    /**
     * Dung than JSON bang Jackson chu khong noi chuoi: tieu de va noi dung deu la tieng Viet co
     * dau va co the chua dau nhay kep hoac ky tu xuong dong. Noi chuoi bang tay se sinh ra JSON
     * hong ngay khi mot dia chi hay mot tua sach chua ky tu dac biet.
     */
    String thanRequest(String to, String subject, String text) {
        ObjectNode goc = objectMapper.createObjectNode();
        ArrayNode danhSach = goc.putArray("Messages");

        ObjectNode thu = danhSach.addObject();
        thu.putObject("From").put("Email", fromAddress);
        thu.putArray("To").addObject().put("Email", to);
        thu.put("Subject", subject);
        thu.put("HTMLPart", text);

        return goc.toString();
    }
}
