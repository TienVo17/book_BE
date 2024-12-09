package com.example.book_be.services.cart;

import com.example.book_be.dao.GioHangRepository;
import com.example.book_be.dao.NguoiDungRepository;
import com.example.book_be.dao.SachRepository;
import com.example.book_be.entity.GioHang;
import com.example.book_be.entity.NguoiDung;
import com.example.book_be.entity.Sach;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CartServiceImpl implements CartService {
    private final ObjectMapper objectMapper;
    @Autowired
    public NguoiDungRepository nguoiDungRepository;
    @Autowired
    public GioHangRepository gioHangRepository;
    @Autowired
    private SachRepository sachRepository;
    public CartServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ResponseEntity<?> save(JsonNode jsonData) {
        try {
            int maNguoiDung = 0;
            List<GioHang> gioHangDataList = new ArrayList<>();

            // Duyệt qua từng item trong jsonData và chuyển đổi thành đối tượng GioHang
            for (JsonNode jsonNode1 : jsonData) {
                GioHang dulieuGioHang = objectMapper.treeToValue(jsonNode1, GioHang.class);
                maNguoiDung = Integer.parseInt(formatStringByJson(String.valueOf(jsonNode1.get("maNguoiDung"))));

                // Kiểm tra sự tồn tại của đối tượng Sach trong cơ sở dữ liệu
                int maSach = jsonNode1.get("maSach").asInt();
                Optional<Sach> sachOptional = sachRepository.findById((long) maSach);
                if (!sachOptional.isPresent()) {
                    return ResponseEntity.badRequest().body("Sách với mã " + maSach + " không tồn tại.");
                }

                // Thiết lập đối tượng Sach cho GioHang
                dulieuGioHang.setSach(sachOptional.get());
                gioHangDataList.add(dulieuGioHang);
            }

            Optional<NguoiDung> nguoiDung = nguoiDungRepository.findById((long) maNguoiDung);
            if (!nguoiDung.isPresent()) {
                return ResponseEntity.badRequest().body("Người dùng không tồn tại.");
            }

            List<GioHang> gioHangList = nguoiDung.get().getDanhSachGioHang();

            for (GioHang gioHang : gioHangDataList) {
                boolean isHad = false;
                for (GioHang gioHang1 : gioHangList) {
                    if (gioHang1.getSach().getMaSach() == gioHang.getSach().getMaSach()) {
                        gioHang1.setSoLuong(gioHang1.getSoLuong() + gioHang.getSoLuong());
                        isHad = true;
                        break;
                    }
                }

                if (!isHad) {
                    GioHang gioHang1 = new GioHang();
                    gioHang1.setNguoiDung(nguoiDung.get());
                    gioHang1.setSoLuong(gioHang.getSoLuong());
                    gioHang1.setSach(gioHang.getSach());
                    gioHangList.add(gioHang1);
                }
            }

            nguoiDung.get().setDanhSachGioHang(gioHangList);
            NguoiDung nguoiDung1 = nguoiDungRepository.save(nguoiDung.get());

            if (gioHangDataList.size() == 1) {
                List<GioHang> gioHangList1 = nguoiDung1.getDanhSachGioHang();
                return ResponseEntity.ok(gioHangList1.get(gioHangList.size() - 1).getMaGioHang());
            }

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }


    @Override
    public ResponseEntity<?> update(JsonNode jsonData) {
        try {
            int maGioHang = Integer.parseInt(formatStringByJson(String.valueOf(jsonData.get("maGioHang"))));
            int soLuong = Integer.parseInt(formatStringByJson(String.valueOf(jsonData.get("soLuong"))));
            Optional<GioHang> gioHang = gioHangRepository.findById(maGioHang);
            gioHang.get().setSoLuong(soLuong);
            gioHangRepository.save(gioHang.get());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    private String formatStringByJson(String json) {
        return json.replaceAll("\"", "");
    }
}