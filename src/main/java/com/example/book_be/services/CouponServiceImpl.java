package com.example.book_be.services;

import com.example.book_be.dao.CouponRepository;
import com.example.book_be.entity.Coupon;
import com.example.book_be.entity.LoaiGiamGia;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CouponServiceImpl implements CouponService {

    @Autowired
    private CouponRepository couponRepository;

    @Override
    public Map<String, Object> kiemTra(String ma, double tongTien) {
        Map<String, Object> result = new HashMap<>();

        Coupon coupon = couponRepository.findByMa(ma.toUpperCase()).orElse(null);

        if (coupon == null) {
            result.put("hopLe", false);
            result.put("thongBao", "Mã giảm giá không tồn tại");
            return result;
        }

        if (!Boolean.TRUE.equals(coupon.getIsActive())) {
            result.put("hopLe", false);
            result.put("thongBao", "Mã giảm giá không còn hiệu lực");
            return result;
        }

        if (coupon.getHanSuDung() != null && coupon.getHanSuDung().before(new Date())) {
            result.put("hopLe", false);
            result.put("thongBao", "Mã giảm giá đã hết hạn");
            return result;
        }

        if (coupon.getSoLuongToiDa() > 0 && coupon.getDaSuDung() >= coupon.getSoLuongToiDa()) {
            result.put("hopLe", false);
            result.put("thongBao", "Mã giảm giá đã hết lượt sử dụng");
            return result;
        }

        if (tongTien < coupon.getGiaTriToiThieu()) {
            result.put("hopLe", false);
            result.put("thongBao", "Đơn hàng chưa đạt giá trị tối thiểu " + coupon.getGiaTriToiThieu());
            return result;
        }

        double giaTriGiam;
        if (LoaiGiamGia.PERCENT.equals(coupon.getLoai())) {
            giaTriGiam = tongTien * coupon.getGiaTriGiam() / 100.0;
        } else {
            giaTriGiam = coupon.getGiaTriGiam();
        }

        // Cap discount at total order amount
        if (giaTriGiam > tongTien) {
            giaTriGiam = tongTien;
        }

        result.put("hopLe", true);
        result.put("thongBao", "Áp dụng mã giảm giá thành công");
        result.put("giaTriGiam", giaTriGiam);
        result.put("tongTienSauGiam", tongTien - giaTriGiam);
        result.put("coupon", coupon);

        return result;
    }

    @Override
    public List<Coupon> findAll() {
        return couponRepository.findAll();
    }

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon.getMa() != null) {
            coupon.setMa(coupon.getMa().toUpperCase());
        }
        return couponRepository.save(coupon);
    }

    @Override
    public Coupon update(int id, Coupon coupon) {
        Coupon db = couponRepository.findById((long) id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy coupon với id: " + id));

        if (coupon.getMa() != null) {
            db.setMa(coupon.getMa().toUpperCase());
        }
        if (coupon.getLoai() != null) {
            db.setLoai(coupon.getLoai());
        }
        db.setGiaTriGiam(coupon.getGiaTriGiam());
        db.setGiaTriToiThieu(coupon.getGiaTriToiThieu());
        if (coupon.getHanSuDung() != null) {
            db.setHanSuDung(coupon.getHanSuDung());
        }
        db.setSoLuongToiDa(coupon.getSoLuongToiDa());
        if (coupon.getIsActive() != null) {
            db.setIsActive(coupon.getIsActive());
        }

        return couponRepository.save(db);
    }

    @Override
    public void delete(int id) {
        couponRepository.deleteById((long) id);
    }
}
