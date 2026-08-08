package com.example.book_be.nhantin.service;

import com.example.book_be.nhantin.domain.DangKyNhanTin;
import com.example.book_be.nhantin.repository.DangKyNhanTinRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class NhanTinService {

    /**
     * Co y de long leo. Bo loc chat hon se tu choi nhung dia chi hop le that (nhan hieu moi,
     * ky tu unicode) va bien mot form dang ky thanh cai bay. Xac thuc that su la buoc gui thu
     * xac nhan, khong phai bieu thuc chinh quy.
     */
    private static final Pattern DANG_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");
    private static final int DO_DAI_EMAIL_TOI_DA = 255;

    private final DangKyNhanTinRepository repository;

    public NhanTinService(DangKyNhanTinRepository repository) {
        this.repository = repository;
    }

    /**
     * Dang ky lai cung mot email khong phai loi va khong tao them dong.
     *
     * <p>Bao 409 cho lan bam thu hai la ep nguoi dung xu ly mot tinh huong khong phai van de
     * cua ho — ho chi muon nhan tin, va ket qua mong muon da dat duoc. Cung ly do khien luong
     * gui danh gia nuot loi trung lap thay vi hien "that bai" cho mot thao tac da thanh cong.
     */
    @Transactional(rollbackFor = Exception.class)
    public void dangKy(String emailThoc) {
        String email = chuanHoa(emailThoc);

        Optional<DangKyNhanTin> daCo = repository.findByEmail(email);
        if (daCo.isPresent()) {
            DangKyNhanTin ban = daCo.get();
            // Dang ky lai sau khi da huy thi bat lai, chu khong im lang bo qua.
            if (Boolean.TRUE.equals(ban.getDaHuy())) {
                ban.setDaHuy(false);
                ban.setNgayDangKy(Instant.now());
                repository.save(ban);
            }
            return;
        }

        DangKyNhanTin moi = new DangKyNhanTin();
        moi.setEmail(email);
        moi.setMaHuy(UUID.randomUUID().toString());
        moi.setNgayDangKy(Instant.now());
        moi.setDaHuy(false);
        try {
            repository.save(moi);
        } catch (DataIntegrityViolationException trungKhoa) {
            // Hai request cung email den cung luc: ca hai deu thay findByEmail rong roi cung
            // ghi. Rang buoc UNIQUE la thu chan that; o day chi can coi nhu da dang ky.
            repository.findByEmail(email).orElseThrow(() -> trungKhoa);
        }
    }

    /** Huy bang khoa ngau nhien. Khoa sai tra 404 chu khong tiet lo email nao ton tai. */
    @Transactional(rollbackFor = Exception.class)
    public void huy(String maHuy) {
        DangKyNhanTin ban = repository.findByMaHuy(maHuy == null ? "" : maHuy.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Liên kết huỷ đăng ký không hợp lệ hoặc đã hết hiệu lực."));
        if (!Boolean.TRUE.equals(ban.getDaHuy())) {
            ban.setDaHuy(true);
            repository.save(ban);
        }
    }

    private String chuanHoa(String email) {
        String giaTri = email == null ? "" : email.trim();
        if (giaTri.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập email.");
        }
        if (giaTri.length() > DO_DAI_EMAIL_TOI_DA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email quá dài.");
        }
        if (!DANG_EMAIL.matcher(giaTri).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email không hợp lệ.");
        }
        // Ha chu thuong de "A@x.com" va "a@x.com" khong thanh hai dong. Dung Locale.ROOT vi
        // locale Tho Nhi Ky ha chu "I" thanh "i" khong cham, lam lech ca dia chi ASCII.
        return giaTri.toLowerCase(java.util.Locale.ROOT);
    }
}
