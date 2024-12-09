package com.example.book_be.services.cart;

import com.example.book_be.dao.ChiTietDonHangRepository;
import com.example.book_be.dao.DonHangRepository;
import com.example.book_be.dao.NguoiDungRepository;
import com.example.book_be.dao.SachRepository;
import com.example.book_be.entity.ChiTietDonHang;
import com.example.book_be.entity.DonHang;
import com.example.book_be.entity.NguoiDung;
import com.example.book_be.entity.Sach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private DonHangRepository donHangRepository;

    @Autowired
    private NguoiDungRepository nguoiDungRepository;

    @Autowired
    private ChiTietDonHangRepository chiTietDonHangRepository;

    @Autowired
    private SachRepository sachRepository;

    @Override
    public DonHang saveOrUpdate(List<Sach> sachList) {
        NguoiDung nguoiDung = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.getName() != null) {
            nguoiDung = nguoiDungRepository.findByTenDangNhap(authentication.getName());
        }
        NguoiDung finalNguoiDung = nguoiDung;
        List<DonHang> donHangs = this.donHangRepository.findAll((root, query, builder) -> builder.and(
                builder.equal(root.get("nguoiDung").get("maNguoiDung"), finalNguoiDung.getMaNguoiDung()),
                builder.equal(root.get("trangThaiThanhToan"), 0)
        ));
        donHangRepository.deleteAll(donHangs);
        DonHang donHang = new DonHang();
        donHang.setNgayTao(new Date());
        donHang.setDiaChiMuaHang(nguoiDung.getDiaChiMuaHang());
        donHang.setDiaChiNhanHang(nguoiDung.getDiaChiGiaoHang());
        donHang.setNguoiDung(nguoiDung);
        donHang.setTrangThaiThanhToan(0);

        List<Integer> integers = new ArrayList<>();
        double tongTienSanPham = 0F;
        for (Sach sach : sachList) {
            Sach db = sachRepository.findById(Long.valueOf(sach.getMaSach())).orElse(null);
            tongTienSanPham += sach.getSoLuong() * db.getGiaBan();
        }
        donHang.setTongTien(tongTienSanPham);
        donHangRepository.save(donHang);

        for (Sach sach : sachList) {
            Sach db = sachRepository.findById(Long.valueOf(sach.getMaSach())).orElse(null);
            ChiTietDonHang chiTietDonHang = new ChiTietDonHang();
            chiTietDonHang.setDonHang(donHang);
            chiTietDonHang.setSach(db);
            chiTietDonHang.setSoLuong(sach.getSoLuong());
            chiTietDonHang.setGiaBan(db.getGiaBan());
            chiTietDonHang.setDanhGia(true);
            chiTietDonHangRepository.save(chiTietDonHang);
        }
        return donHang;
    }
}