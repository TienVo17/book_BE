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

@Testcontainers
class V14ReviewImageIdempotencyTest {

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
    void migrate_tu_v13_backfill_anh_cu_va_siet_contract() throws Exception {
        migrateDen("13");

        long maDanhGia;
        long maHinhAnh;
        try (Connection ket = ketNoi()) {
            maDanhGia = motSo(ket, "SELECT MIN(`ma_danh_gia`) FROM `danhgia`");
            try (Statement st = ket.createStatement()) {
                st.executeUpdate("INSERT INTO `danhgia_hinh_anh` "
                                + "(`ma_danh_gia`, `url_hinh`, `cloudinary_public_id`, `thu_tu`, `tao_luc`) VALUES ("
                                + maDanhGia + ", 'https://cdn.example/pre-v14.png', 'pre-v14-public', "
                                + "0, CURRENT_TIMESTAMP)",
                        Statement.RETURN_GENERATED_KEYS);
                try (ResultSet rs = st.getGeneratedKeys()) {
                    assertThat(rs.next()).isTrue();
                    maHinhAnh = rs.getLong(1);
                }
            }
        }

        migrateDen("14");

        try (Connection ket = ketNoi()) {
            assertThat(motDong(ket, "SELECT CONCAT_WS('|', `idempotency_key`, `noi_dung_sha256`) "
                    + "FROM `danhgia_hinh_anh` WHERE `ma_hinh_anh` = " + maHinhAnh))
                    .isEqualTo("legacy-" + maHinhAnh + "|" + "0".repeat(64));
            assertThat(motDong(ket, "SELECT GROUP_CONCAT(CONCAT(`COLUMN_NAME`, ':', `IS_NULLABLE`) "
                    + "ORDER BY `COLUMN_NAME`) FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'danhgia_hinh_anh' "
                    + "AND COLUMN_NAME IN ('idempotency_key', 'noi_dung_sha256')"))
                    .isEqualTo("idempotency_key:NO,noi_dung_sha256:NO");
            assertThat(motDong(ket, "SELECT GROUP_CONCAT(`COLUMN_NAME` ORDER BY `SEQ_IN_INDEX`) "
                    + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'danhgia_hinh_anh' "
                    + "AND INDEX_NAME = 'uk_review_image_idempotency' AND NON_UNIQUE = 0"))
                    .isEqualTo("ma_danh_gia,idempotency_key");
        }
    }

    @Test
    void chay_lai_v14_khong_doi_du_lieu_hoac_schema() throws Exception {
        migrateDen("14");

        try (Connection ket = ketNoi()) {
            long maDanhGia = motSo(ket, "SELECT MIN(`ma_danh_gia`) FROM `danhgia`");
            long maHinhAnh;
            try (Statement st = ket.createStatement()) {
                st.executeUpdate("INSERT INTO `danhgia_hinh_anh` "
                        + "(`ma_danh_gia`, `url_hinh`, `cloudinary_public_id`, `idempotency_key`, "
                        + "`noi_dung_sha256`, `thu_tu`, `tao_luc`) VALUES ("
                        + maDanhGia + ", 'https://cdn.example/legacy.png', 'legacy-public', "
                        + "'legacy-manual', REPEAT('0', 64), 0, CURRENT_TIMESTAMP)",
                        Statement.RETURN_GENERATED_KEYS);
                try (ResultSet rs = st.getGeneratedKeys()) {
                    assertThat(rs.next()).isTrue();
                    maHinhAnh = rs.getLong(1);
                }
            }

            Map<String, Object> truoc = chupAnh(ket, maHinhAnh);
            chayLaiV14(ket);

            assertThat(chupAnh(ket, maHinhAnh))
                    .as("chay lai V14 phai la no-op tren row va metadata")
                    .isEqualTo(truoc);
            assertThat(chenTrungKhoaBiTuChoi(ket, maDanhGia)).isTrue();
        }
    }

    private void migrateDen(String phienBan) {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(phienBan)
                .load()
                .migrate();
    }

    private Connection ketNoi() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private void chayLaiV14(Connection ket) throws Exception {
        for (String cau : docCacCauLenhV14()) {
            try (Statement st = ket.createStatement()) {
                st.execute(cau);
            }
        }
    }

    private Map<String, Object> chupAnh(Connection ket, long maHinhAnh) throws SQLException {
        Map<String, Object> anh = new LinkedHashMap<>();
        anh.put("row", motDong(ket, "SELECT CONCAT_WS('|', `ma_danh_gia`, `url_hinh`, "
                + "`cloudinary_public_id`, `idempotency_key`, `noi_dung_sha256`, `thu_tu`) "
                + "FROM `danhgia_hinh_anh` WHERE `ma_hinh_anh` = " + maHinhAnh));
        anh.put("nullable", motDong(ket, "SELECT GROUP_CONCAT(CONCAT(`COLUMN_NAME`, ':', `IS_NULLABLE`) "
                + "ORDER BY `COLUMN_NAME`) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'danhgia_hinh_anh' "
                + "AND COLUMN_NAME IN ('idempotency_key', 'noi_dung_sha256')"));
        anh.put("unique", motDong(ket, "SELECT GROUP_CONCAT(`COLUMN_NAME` ORDER BY `SEQ_IN_INDEX`) "
                + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'danhgia_hinh_anh' "
                + "AND INDEX_NAME = 'uk_review_image_idempotency' AND NON_UNIQUE = 0"));
        return anh;
    }

    private boolean chenTrungKhoaBiTuChoi(Connection ket, long maDanhGia) throws SQLException {
        try (Statement st = ket.createStatement()) {
            st.executeUpdate("INSERT INTO `danhgia_hinh_anh` "
                    + "(`ma_danh_gia`, `url_hinh`, `cloudinary_public_id`, `idempotency_key`, "
                    + "`noi_dung_sha256`, `thu_tu`, `tao_luc`) VALUES ("
                    + maDanhGia + ", 'https://cdn.example/duplicate.png', 'duplicate-public', "
                    + "'legacy-manual', REPEAT('1', 64), 1, CURRENT_TIMESTAMP)");
            return false;
        } catch (SQLException expected) {
            return "23000".equals(expected.getSQLState());
        }
    }

    private long motSo(Connection ket, String sql) throws SQLException {
        try (Statement st = ket.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }

    private String motDong(Connection ket, String sql) throws SQLException {
        try (Statement st = ket.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }

    private List<String> docCacCauLenhV14() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(
                "/db/migration/V14__review_image_idempotency.sql")) {
            assertThat(in).as("phai tim thay V14 tren classpath").isNotNull();
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
