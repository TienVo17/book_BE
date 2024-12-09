package com.example.book_be.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Date;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "phan_hoi")
public class PhanHoi {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_phan_hoi")
    private int maPhanHoi; // Mã phản hồi

    @Column(name = "tieu_de")
    private String tieuDe; // Tiêu đề phản hồi

    @Column(name = "binh_luan")
    private String binhLuan; // Nội dung bình luận

    @Column(name = "ngay_tao")
    private Date ngayTao; // Ngày tạo phản hồi

    @Column(name = "da_doc")
    private boolean daDoc; // Đã được đọc hay chưa

    @ManyToOne(cascade = {CascadeType.DETACH, CascadeType.MERGE, CascadeType.PERSIST, CascadeType.REFRESH})
    @JoinColumn(name = "ma_nguoi_dung", nullable = false)
    private NguoiDung nguoiDung; // Người dùng

    @Override
    public String toString() {
        return "PhanHoi{" +
                "maPhanHoi=" + maPhanHoi +
                ", tieuDe='" + tieuDe + '\'' +
                ", binhLuan='" + binhLuan + '\'' +
                ", ngayTao=" + ngayTao +
                ", daDoc=" + daDoc +
                '}';
    }
}
