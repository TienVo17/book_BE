package com.example.book_be.services;

import com.example.book_be.dao.DiaChiGiaoHangRepository;
import com.example.book_be.dao.NguoiDungRepository;
import com.example.book_be.entity.DiaChiGiaoHang;
import com.example.book_be.entity.NguoiDung;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DiaChiServiceImpl implements DiaChiService {

    @Autowired
    private DiaChiGiaoHangRepository diaChiRepo;

    @Autowired
    private NguoiDungRepository nguoiDungRepository;

    @Override
    public List<DiaChiGiaoHang> findByNguoiDung(int maNguoiDung) {
        return diaChiRepo.findByNguoiDung_MaNguoiDung(maNguoiDung);
    }

    @Override
    public DiaChiGiaoHang save(int maNguoiDung, DiaChiGiaoHang diaChi) {
        List<DiaChiGiaoHang> existing = diaChiRepo.findByNguoiDung_MaNguoiDung(maNguoiDung);
        if (existing.size() >= 10) {
            throw new RuntimeException("Tối đa 10 địa chỉ giao hàng");
        }

        NguoiDung nguoiDung = nguoiDungRepository.findById((long) maNguoiDung)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        diaChi.setNguoiDung(nguoiDung);

        // If new address is default, reset all others
        if (Boolean.TRUE.equals(diaChi.getMacDinh())) {
            resetMacDinh(existing);
        }

        return diaChiRepo.save(diaChi);
    }

    @Override
    public DiaChiGiaoHang update(int maNguoiDung, int maDiaChi, DiaChiGiaoHang diaChi) {
        DiaChiGiaoHang db = diaChiRepo.findById((long) maDiaChi)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy địa chỉ"));

        // Ownership check
        if (db.getNguoiDung().getMaNguoiDung() != maNguoiDung) {
            throw new RuntimeException("Không có quyền sửa địa chỉ này");
        }

        db.setHoTen(diaChi.getHoTen());
        db.setSoDienThoai(diaChi.getSoDienThoai());
        db.setDiaChiDayDu(diaChi.getDiaChiDayDu());

        // Handle macDinh: reset others only when setting this one as default
        if (Boolean.TRUE.equals(diaChi.getMacDinh()) && !Boolean.TRUE.equals(db.getMacDinh())) {
            List<DiaChiGiaoHang> allAddresses = diaChiRepo.findByNguoiDung_MaNguoiDung(maNguoiDung);
            resetMacDinh(allAddresses);
        }
        db.setMacDinh(diaChi.getMacDinh());

        return diaChiRepo.save(db);
    }

    @Override
    public void delete(int maNguoiDung, int maDiaChi) {
        DiaChiGiaoHang db = diaChiRepo.findById((long) maDiaChi)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy địa chỉ"));

        // Ownership check
        if (db.getNguoiDung().getMaNguoiDung() != maNguoiDung) {
            throw new RuntimeException("Không có quyền xóa địa chỉ này");
        }

        diaChiRepo.deleteById((long) maDiaChi);
    }

    // Reset all addresses' macDinh flag to false
    private void resetMacDinh(List<DiaChiGiaoHang> addresses) {
        for (DiaChiGiaoHang a : addresses) {
            if (Boolean.TRUE.equals(a.getMacDinh())) {
                a.setMacDinh(false);
                diaChiRepo.save(a);
            }
        }
    }
}
