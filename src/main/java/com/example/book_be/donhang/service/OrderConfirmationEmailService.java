package com.example.book_be.donhang.service;

import com.example.book_be.donhang.domain.ChiTietDonHang;
import com.example.book_be.donhang.domain.DonHang;
import com.example.book_be.shared.email.EmailService;
import com.example.book_be.shared.email.HtmlEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class OrderConfirmationEmailService {

    private static final Logger log = LoggerFactory.getLogger(OrderConfirmationEmailService.class);
    private static final ZoneId VIETNAM_TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withLocale(Locale.forLanguageTag("vi-VN"));

    private final EmailService emailService;

    public OrderConfirmationEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * Capture toan bo du lieu can gui khi entity con managed, sau do chi gui khi checkout da commit.
     */
    public void scheduleAfterCommit(DonHang donHang, List<ChiTietDonHang> lineItems) {
        if (donHang == null || donHang.getNguoiDung() == null) {
            return;
        }
        String recipient = donHang.getNguoiDung().getEmail();
        if (recipient == null || recipient.isBlank()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("event=email_skipped type=order_confirmation reason=no_transaction_synchronization");
            return;
        }

        OrderEmailSnapshot snapshot = snapshotOf(donHang, lineItems, recipient);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendSafely(snapshot);
            }
        });
    }

    private OrderEmailSnapshot snapshotOf(DonHang donHang, List<ChiTietDonHang> lineItems,
                                           String recipient) {
        List<OrderLineSnapshot> lines = lineItems == null
                ? List.of()
                : lineItems.stream()
                .map(line -> new OrderLineSnapshot(
                        line.getSach() == null ? "" : line.getSach().getTenSach(),
                        line.getSoLuong(),
                        line.getGiaBan()))
                .toList();
        String paymentMethod = donHang.getHinhThucThanhToan() == null
                ? ""
                : donHang.getHinhThucThanhToan().getTenHinhThucGiaoHang();
        String deliveryMethod = donHang.getHinhThucGiaoHang() == null
                ? ""
                : donHang.getHinhThucGiaoHang().getTenHinhThucGiaoHang();
        double discount = Math.max(0, donHang.getTongTienSanPham()
                + donHang.getChiPhiGiaoHang()
                + donHang.getChiPhiThanhToan()
                - donHang.getTongTien());
        String createdAt = donHang.getNgayTao() == null
                ? ""
                : DATE_FORMATTER.format(donHang.getNgayTao().toInstant().atZone(VIETNAM_TIME_ZONE));

        return new OrderEmailSnapshot(
                recipient,
                donHang.getMaDonHang(),
                donHang.getHoTen(),
                createdAt,
                donHang.getDiaChiNhanHang(),
                paymentMethod,
                deliveryMethod,
                donHang.getTongTienSanPham(),
                discount,
                donHang.getChiPhiGiaoHang(),
                donHang.getChiPhiThanhToan(),
                donHang.getTongTien(),
                lines);
    }

    private void sendSafely(OrderEmailSnapshot snapshot) {
        try {
            emailService.sendEmail(
                    snapshot.recipient(),
                    "Xác nhận đơn hàng #" + snapshot.orderId(),
                    buildEmailBody(snapshot));
        } catch (Exception exception) {
            log.warn("event=email_failed type=order_confirmation exception={}",
                    exception.getClass().getSimpleName());
        }
    }

    private String buildEmailBody(OrderEmailSnapshot snapshot) {
        StringBuilder rows = new StringBuilder();
        for (OrderLineSnapshot line : snapshot.lines()) {
            rows.append("<tr>")
                    .append(cell(line.title()))
                    .append(cell(line.quantity()))
                    .append(cell(formatMoney(line.unitPrice())))
                    .append(cell(formatMoney(line.unitPrice() * line.quantity())))
                    .append("</tr>");
        }

        return "<html><body>"
                + "<h2 style=\"border-bottom:2px solid #333;padding-bottom:10px;\">Xác nhận đơn hàng</h2>"
                + "<p>Chào " + HtmlEncoder.encode(snapshot.customerName()) + ",</p>"
                + "<p>Cảm ơn bạn đã đặt hàng. Đơn hàng của bạn đã được ghi nhận thành công.</p>"
                + detail("Mã đơn hàng", "#" + snapshot.orderId())
                + detail("Ngày đặt hàng", snapshot.createdAt())
                + detail("Phương thức thanh toán", snapshot.paymentMethod())
                + detail("Hình thức giao hàng", snapshot.deliveryMethod())
                + "<table style=\"width:100%;border:1px solid #ddd;border-collapse:collapse;\">"
                + "<thead style=\"background-color:#f4f4f4;\"><tr>"
                + heading("Tên sách") + heading("Số lượng")
                + heading("Đơn giá") + heading("Thành tiền")
                + "</tr></thead><tbody>" + rows + "</tbody></table>"
                + moneyDetail("Tạm tính", snapshot.subtotal())
                + moneyDetail("Giảm giá", snapshot.discount())
                + moneyDetail("Phí giao hàng", snapshot.shippingFee())
                + moneyDetail("Phí thanh toán", snapshot.paymentFee())
                + "<p style=\"color:red;border-top:2px solid red;padding-top:10px;\"><b>Tổng tiền: "
                + HtmlEncoder.encode(formatMoney(snapshot.total())) + "</b></p>"
                + detail("Địa chỉ nhận hàng", snapshot.deliveryAddress())
                + "<p style=\"border-top:1px solid #ddd;padding-top:10px;\">"
                + "Đơn hàng của bạn sẽ được xử lý trong thời gian sớm nhất.</p>"
                + "<p>Trân trọng cảm ơn!</p>"
                + "</body></html>";
    }

    private String heading(String value) {
        return "<th style=\"border:1px solid #ddd;padding:8px;text-align:left;\">"
                + HtmlEncoder.encode(value) + "</th>";
    }

    private String cell(Object value) {
        return "<td style=\"border:1px solid #ddd;padding:8px;\">"
                + HtmlEncoder.encode(value) + "</td>";
    }

    private String detail(String label, Object value) {
        return "<p><b>" + HtmlEncoder.encode(label) + ":</b> " + HtmlEncoder.encode(value) + "</p>";
    }

    private String moneyDetail(String label, double value) {
        return detail(label, formatMoney(value));
    }

    private String formatMoney(double value) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        formatter.setMaximumFractionDigits(0);
        formatter.setMinimumFractionDigits(0);
        return formatter.format(value) + " đ";
    }

    private record OrderEmailSnapshot(
            String recipient,
            int orderId,
            String customerName,
            String createdAt,
            String deliveryAddress,
            String paymentMethod,
            String deliveryMethod,
            double subtotal,
            double discount,
            double shippingFee,
            double paymentFee,
            double total,
            List<OrderLineSnapshot> lines) {
    }

    private record OrderLineSnapshot(String title, int quantity, double unitPrice) {
    }
}
