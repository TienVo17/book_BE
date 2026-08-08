package com.example.book_be.nhantin.service;

import com.example.book_be.nhantin.domain.DangKyNhanTin;
import com.example.book_be.nhantin.repository.DangKyNhanTinRepository;
import com.example.book_be.shared.config.FrontendUrlProvider;
import com.example.book_be.shared.email.EmailService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
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
    /** Khoa xac nhan het han sau bay ngay; qua han thi dang ky lai de nhan khoa moi. */
    private static final Duration HAN_XAC_NHAN = Duration.ofDays(7);

    private final DangKyNhanTinRepository repository;
    private final EmailService emailService;
    private final FrontendUrlProvider frontendUrlProvider;

    public NhanTinService(DangKyNhanTinRepository repository,
                          EmailService emailService,
                          FrontendUrlProvider frontendUrlProvider) {
        this.repository = repository;
        this.emailService = emailService;
        this.frontendUrlProvider = frontendUrlProvider;
    }

    /**
     * Dang ky la buoc MOT trong hai buoc: dia chi duoc ghi lai o trang thai cho, va chi vao
     * danh sach gui sau khi chinh chu bam lien ket trong thu xac thuc.
     *
     * <p>Xac nhan mot lan nghia la bat ky ai cung go duoc email nguoi khac vao o o footer, va
     * danh sach khong chung minh duoc su dong y — dieu Nghi dinh 91/2020 doi hoi. No con lam
     * hong uy tin nguoi gui: dia chi khong dong y se bao cao thu rac.
     */
    @Transactional(rollbackFor = Exception.class)
    public void dangKy(String emailThoc) {
        String email = chuanHoa(emailThoc);

        Optional<DangKyNhanTin> daCo = repository.findByEmail(email);
        if (daCo.isPresent()) {
            DangKyNhanTin ban = daCo.get();
            if (Boolean.TRUE.equals(ban.getDaXacNhan()) && !Boolean.TRUE.equals(ban.getDaHuy())) {
                // Da o trong danh sach roi. Khong gui lai thu, vi nhu vay bat ky ai cung
                // bien o dang ky thanh cong cu spam mot dia chi bang cach bam lien tuc.
                return;
            }
            ban.setDaHuy(false);
            ban.setDaXacNhan(false);
            ban.setNgayXacNhan(null);
            ban.setMaXacNhan(UUID.randomUUID().toString());
            ban.setNgayDangKy(Instant.now());
            repository.save(ban);
            guiThuXacThuc(ban);
            return;
        }

        DangKyNhanTin moi = new DangKyNhanTin();
        moi.setEmail(email);
        moi.setMaHuy(UUID.randomUUID().toString());
        moi.setMaXacNhan(UUID.randomUUID().toString());
        moi.setNgayDangKy(Instant.now());
        moi.setDaHuy(false);
        moi.setDaXacNhan(false);
        try {
            repository.save(moi);
        } catch (DataIntegrityViolationException trungKhoa) {
            // Hai request cung email den cung luc deu thay findByEmail rong roi cung ghi.
            // Rang buoc UNIQUE la thu chan that; o day chi can coi nhu da co ban ghi.
            repository.findByEmail(email).orElseThrow(() -> trungKhoa);
            return;
        }
        guiThuXacThuc(moi);
    }

    /**
     * Buoc HAI: chu dia chi bam lien ket trong thu. Chi tu day dia chi moi nam trong danh sach.
     *
     * <p>Khoa duoc xoa sau khi dung, nen mot lien ket bi lo (chuyen tiep thu, luu lich su) khong
     * con gia tri.
     */
    @Transactional(rollbackFor = Exception.class)
    public void xacNhan(String maXacNhan) {
        DangKyNhanTin ban = repository.findByMaXacNhan(maXacNhan == null ? "" : maXacNhan.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Liên kết xác nhận không hợp lệ hoặc đã được dùng."));

        if (ban.getNgayDangKy() != null
                && ban.getNgayDangKy().plus(HAN_XAC_NHAN).isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Liên kết xác nhận đã hết hạn. Vui lòng đăng ký lại để nhận liên kết mới.");
        }

        ban.setDaXacNhan(true);
        ban.setDaHuy(false);
        ban.setNgayXacNhan(Instant.now());
        ban.setMaXacNhan(null);
        repository.save(ban);
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

    /**
     * Khong gui duoc thu thi dang ky khong bao gio hoan tat, nen bao thanh cong la noi doi.
     * Cung nguyen tac ma dang ky tai khoan va quen mat khau dang theo.
     */
    private void guiThuXacThuc(DangKyNhanTin ban) {
        String duongDan = frontendUrlProvider.xacNhanNhanTinUrl(ban.getMaXacNhan());
        String noiDung = "<p>Xin chào,</p>"
                + "<p>Bạn vừa đăng ký nhận tin từ BookStore. Nhấn vào liên kết dưới đây để xác nhận:</p>"
                + "<p><a href=\"" + duongDan + "\">Xác nhận đăng ký nhận tin</a></p>"
                + "<p>Liên kết có hiệu lực trong 7 ngày.</p>"
                + "<p>Nếu bạn không đăng ký, hãy bỏ qua thư này — chúng tôi sẽ không gửi gì thêm.</p>";
        try {
            emailService.sendEmail(ban.getEmail(), "Xác nhận đăng ký nhận tin từ BookStore", noiDung);
        } catch (RuntimeException loiGui) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa gửi được thư xác nhận. Vui lòng thử lại sau.", loiGui);
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
        return giaTri.toLowerCase(Locale.ROOT);
    }
}
