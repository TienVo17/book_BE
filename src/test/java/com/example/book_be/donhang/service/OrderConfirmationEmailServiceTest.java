package com.example.book_be.donhang.service;

import com.example.book_be.donhang.domain.ChiTietDonHang;
import com.example.book_be.donhang.domain.DonHang;
import com.example.book_be.donhang.domain.HinhThucGiaoHang;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.shared.email.EmailService;
import com.example.book_be.thanhtoan.domain.HinhThucThanhToan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OrderConfirmationEmailServiceTest {

    private final EmailService emailService = mock(EmailService.class);
    private final OrderConfirmationEmailService service = new OrderConfirmationEmailService(emailService);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void chi_gui_sau_commit_va_dung_gia_snapshot_da_encode_html() {
        TransactionSynchronizationManager.initSynchronization();
        DonHang order = order("khach@example.test");
        order.setHoTen("Khách <b>xấu</b>");
        order.setDiaChiNhanHang("<a href=javascript:alert(1)>địa chỉ</a>");
        order.getHinhThucThanhToan().setTenHinhThucGiaoHang("<svg onload=alert(1)>");
        order.getHinhThucGiaoHang().setTenHinhThucGiaoHang("Giao <script>nhanh</script>");
        ChiTietDonHang line = line(order, "<img src=x onerror=alert(1)>", 250000, 100000, 2);

        service.scheduleAfterCommit(order, List.of(line));

        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        order.setHoTen("Tên đã thay đổi");
        line.setGiaBan(1);
        line.getSach().setTenSach("Tên sách đã thay đổi");
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(
                org.mockito.ArgumentMatchers.eq("khach@example.test"),
                org.mockito.ArgumentMatchers.eq("Xác nhận đơn hàng #91"),
                body.capture());
        assertThat(body.getValue())
                .contains("&lt;b&gt;")
                .contains("&lt;a href=javascript:alert(1)&gt;")
                .contains("&lt;img src=x onerror=alert(1)&gt;")
                .contains("&lt;svg onload=alert(1)&gt;")
                .contains("Giao &lt;script&gt;nhanh&lt;/script&gt;")
                .contains("100.000")
                .contains("200.000")
                .doesNotContain("250.000")
                .doesNotContain("<b>xấu</b>")
                .doesNotContain("<img src=x onerror=alert(1)>");
    }

    @Test
    void bo_qua_nguoi_nhan_rong() {
        TransactionSynchronizationManager.initSynchronization();

        service.scheduleAfterCommit(order("   "), List.of());

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(emailService);
    }

    @Test
    void loi_email_khong_thoat_khoi_after_commit() {
        TransactionSynchronizationManager.initSynchronization();
        DonHang order = order("khach@example.test");
        doThrow(new IllegalStateException("mail unavailable"))
                .when(emailService).sendEmail(anyString(), anyString(), anyString());

        service.scheduleAfterCommit(order, List.of(line(order, "Sách", 100000, 100000, 1)));
        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().get(0);

        assertThatCode(synchronization::afterCommit).doesNotThrowAnyException();
    }

    private DonHang order(String email) {
        NguoiDung user = new NguoiDung();
        user.setEmail(email);

        HinhThucThanhToan payment = new HinhThucThanhToan();
        payment.setMaCode("COD");
        payment.setTenHinhThucGiaoHang("Thanh toán khi nhận hàng");

        HinhThucGiaoHang delivery = new HinhThucGiaoHang();
        delivery.setTenHinhThucGiaoHang("Giao hàng tận nơi");

        DonHang order = new DonHang();
        order.setMaDonHang(91);
        order.setNgayTao(new Date(1760000000000L));
        order.setNguoiDung(user);
        order.setHoTen("Người nhận");
        order.setDiaChiNhanHang("Địa chỉ nhận hàng");
        order.setHinhThucThanhToan(payment);
        order.setHinhThucGiaoHang(delivery);
        order.setTongTienSanPham(200000);
        order.setChiPhiGiaoHang(10000);
        order.setChiPhiThanhToan(0);
        order.setTongTien(200000);
        return order;
    }

    private ChiTietDonHang line(DonHang order, String title, double currentPrice,
                                double snapshotPrice, int quantity) {
        Sach book = new Sach();
        book.setTenSach(title);
        book.setGiaBan(currentPrice);

        ChiTietDonHang line = new ChiTietDonHang();
        line.setDonHang(order);
        line.setSach(book);
        line.setGiaBan(snapshotPrice);
        line.setSoLuong(quantity);
        return line;
    }
}
