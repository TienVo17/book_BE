package com.example.book_be.shared.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V11 tu nhan la chay lai duoc. Test nay bat no chung minh dieu do.
 *
 * <p>MySQL auto-commit DDL, nen Flyway khong roll back duoc mot migration hong giua chung:
 * khi mot lan deploy dut o giua, nguoi van hanh chi con cach chay lai. Neu lan chay thu hai
 * lam hong du lieu — bo ban luu {@code trung_binh_xep_hang_truoc_v11}, xoa nham dong, hay
 * danh dau thua {@code tung_bi_an} — thi buoc khoi phuc lai la buoc pha hoai.
 */
@Testcontainers
class V11ReviewSchemaIdempotencyTest {

    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0").withCommand("--sql-require-primary-key=ON");

    @BeforeAll
    static void startContainer() {
        MYSQL.start();
    }

    @AfterAll
    static void stopContainer() {
        MYSQL.stop();
    }

    @Test
    void chay_lai_v11_khong_doi_du_lieu() throws Exception {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection ket = ketNoi()) {
            Map<String, Object> truoc = chupAnh(ket);

            for (String cau : docCacCauLenhV11()) {
                try (Statement st = ket.createStatement()) {
                    st.execute(cau);
                }
            }

            assertThat(chupAnh(ket))
                    .as("chay lai V11 phai la no-op tren du lieu")
                    .isEqualTo(truoc);
        }
    }

    /**
     * Ban luu la thu de mat nhat: no chi duoc ghi mot lan, va mot lan ghi de bang gia tri
     * da tinh lai se xoa so duong lui ma khong bao gi.
     */
    @Test
    void chay_lai_v11_khong_ghi_de_ban_luu_diem_cu() throws Exception {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection ket = ketNoi()) {
            String maSach = String.valueOf(motSo(ket, "SELECT MIN(`ma_sach`) FROM `sach`"));

            // Ep mot dong ve dung tinh huong nguy hiem: ban luu NULL nhung diem hien tai
            // da bi buoc backfill ghi de. Neu dieu kien chay lai la "IS NULL", lan chay thu
            // hai se chep 4.5 vao o ban luu va coi do la gia tri goc.
            try (Statement st = ket.createStatement()) {
                st.executeUpdate("UPDATE `sach` SET `trung_binh_xep_hang_truoc_v11` = NULL, "
                        + "`trung_binh_xep_hang` = 4.5 WHERE `ma_sach` = " + maSach);
            }

            for (String cau : docCacCauLenhV11()) {
                try (Statement st = ket.createStatement()) {
                    st.execute(cau);
                }
            }

            assertThat(motSo(ket, "SELECT COUNT(*) FROM `sach` WHERE `ma_sach` = " + maSach
                    + " AND `trung_binh_xep_hang_truoc_v11` IS NOT NULL"))
                    .as("ban luu da bi xoa thi phai giu nguyen NULL, khong duoc dien lai bang gia tri hien tai")
                    .isEqualTo("0");
        }
    }

    private Connection ketNoi() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    /** Anh chup cac dai luong ma lan chay thu hai co the lam hong. */
    private Map<String, Object> chupAnh(Connection ket) throws SQLException {
        Map<String, Object> anh = new LinkedHashMap<>();
        anh.put("soDanhGia", motSo(ket, "SELECT COUNT(*) FROM `danhgia`"));
        anh.put("soTungBiAn", motSo(ket, "SELECT COUNT(*) FROM `danhgia` WHERE `tung_bi_an` = 1"));
        anh.put("soDaAn", motSo(ket, "SELECT COUNT(*) FROM `danhgia` WHERE `trang_thai` = 'DA_AN'"));
        anh.put("tongSoLuot", motSo(ket, "SELECT COALESCE(SUM(`so_luot_danh_gia`), 0) FROM `sach`"));
        anh.put("soBanLuuCoGiaTri",
                motSo(ket, "SELECT COUNT(*) FROM `sach` WHERE `trung_binh_xep_hang_truoc_v11` IS NOT NULL"));
        anh.put("tongBanLuu",
                motSo(ket, "SELECT COALESCE(SUM(`trung_binh_xep_hang_truoc_v11`), 0) FROM `sach`"));
        anh.put("tongDiem", motSo(ket, "SELECT COALESCE(SUM(`trung_binh_xep_hang`), 0) FROM `sach`"));
        return anh;
    }

    private Object motSo(Connection ket, String sql) throws SQLException {
        try (Statement st = ket.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    /**
     * Doc chinh file migration dang chay trong production thay vi chep lai noi dung — mot ban
     * chep se ngung phan anh su that ngay lan sua V11 dau tien.
     */
    private List<String> docCacCauLenhV11() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/db/migration/V11__review_schema_additive.sql")) {
            assertThat(in).as("phai tim thay V11 tren classpath").isNotNull();
            String noiDung = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<String> cacCau = new ArrayList<>();
            for (String cau : noiDung.split(";\\s*\\r?\\n")) {
                String sach = boChuThich(cau).trim();
                if (!sach.isEmpty()) {
                    cacCau.add(sach);
                }
            }
            return cacCau;
        }
    }

    private String boChuThich(String cau) {
        StringBuilder ketQua = new StringBuilder();
        for (String dong : cau.split("\\r?\\n")) {
            if (!dong.stripLeading().startsWith("--")) {
                ketQua.append(dong).append('\n');
            }
        }
        return ketQua.toString();
    }
}
