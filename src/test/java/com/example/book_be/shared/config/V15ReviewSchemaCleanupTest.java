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
class V15ReviewSchemaCleanupTest {

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
    void chi_cleanup_sau_hai_cong_va_chay_lai_khong_doi_schema_hoac_du_lieu() throws Exception {
        migrateDen("14");

        try (Connection ket = ketNoi()) {
            assertThat(motSo(ket,
                    "SELECT COUNT(*) FROM `danhgia` WHERE `ma_don_hang` IS NULL"))
                    .as("cong du lieu phai sach truoc moi DDL V15")
                    .isZero();
            assertThat(motSo(ket,
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'danhgia' "
                            + "AND COLUMN_NAME = 'is_active'"))
                    .as("V14 van con cot bridge de V15 co viec can cleanup")
                    .isOne();
        }

        migrateDen("15");

        try (Connection ket = ketNoi()) {
            khangDinhContractV15(ket);
            Map<String, Object> truoc = chupAnh(ket);

            for (String cau : docCacCauLenhV15()) {
                try (Statement st = ket.createStatement()) {
                    st.execute(cau);
                }
            }

            assertThat(chupAnh(ket))
                    .as("chay lai V15 phai la no-op tren schema va du lieu")
                    .isEqualTo(truoc);
            khangDinhContractV15(ket);
        }
    }

    private void khangDinhContractV15(Connection ket) throws SQLException {
        assertThat(motDong(ket,
                "SELECT CONCAT(`DATA_TYPE`, ':', `IS_NULLABLE`) "
                        + "FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'danhgia' "
                        + "AND COLUMN_NAME = 'ma_don_hang'"))
                .isEqualTo("int:NO");
        assertThat(motSo(ket,
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'danhgia' "
                        + "AND COLUMN_NAME = 'is_active'"))
                .isZero();
        assertThat(motDong(ket,
                "SELECT CONCAT(`COLUMN_NAME`, '->', `REFERENCED_TABLE_NAME`, '.', "
                        + "`REFERENCED_COLUMN_NAME`) FROM information_schema.KEY_COLUMN_USAGE "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'danhgia' "
                        + "AND CONSTRAINT_NAME = 'fk_danhgia_don_hang'"))
                .isEqualTo("ma_don_hang->don_hang.ma_don_hang");

        long maDanhGia = motSo(ket, "SELECT MIN(`ma_danh_gia`) FROM `danhgia`");
        assertThat(updateBiTuChoi(ket,
                "UPDATE `danhgia` SET `ma_don_hang` = NULL WHERE `ma_danh_gia` = " + maDanhGia))
                .as("NOT NULL phai chan review mat bang chung don hang")
                .isTrue();
        assertThat(updateBiTuChoi(ket,
                "UPDATE `danhgia` SET `ma_don_hang` = 2147483647 WHERE `ma_danh_gia` = " + maDanhGia))
                .as("FK phai chan review tro toi don hang khong ton tai")
                .isTrue();
    }

    private Map<String, Object> chupAnh(Connection ket) throws SQLException {
        Map<String, Object> anh = new LinkedHashMap<>();
        anh.put("soDanhGia", motSo(ket, "SELECT COUNT(*) FROM `danhgia`"));
        anh.put("tongDon", motSo(ket,
                "SELECT COALESCE(SUM(`ma_don_hang`), 0) FROM `danhgia`"));
        anh.put("maDonHang", motDong(ket,
                "SELECT CONCAT(`DATA_TYPE`, ':', `IS_NULLABLE`) "
                        + "FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'danhgia' "
                        + "AND COLUMN_NAME = 'ma_don_hang'"));
        anh.put("cotCu", motSo(ket,
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'danhgia' "
                        + "AND COLUMN_NAME = 'is_active'"));
        anh.put("fk", motSo(ket,
                "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'danhgia' "
                        + "AND CONSTRAINT_NAME = 'fk_danhgia_don_hang' "
                        + "AND CONSTRAINT_TYPE = 'FOREIGN KEY'"));
        return anh;
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
        return DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private boolean updateBiTuChoi(Connection ket, String sql) throws SQLException {
        try (Statement st = ket.createStatement()) {
            st.executeUpdate(sql);
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

    private List<String> docCacCauLenhV15() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(
                "/db/migration/V15__review_schema_cleanup.sql")) {
            assertThat(in).as("phai tim thay V15 tren classpath").isNotNull();
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
