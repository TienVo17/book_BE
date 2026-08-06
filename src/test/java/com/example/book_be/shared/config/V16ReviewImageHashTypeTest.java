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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code danhgia_hinh_anh.noi_dung_sha256} phai la VARCHAR(64), khong phai CHAR(64).
 *
 * <p>V14 tao cot nay bang CHAR(64) trong khi entity map {@code String} voi
 * {@code length = 64}, tuc VARCHAR. Voi {@code ddl-auto=validate} — cau hinh that cua
 * {@code application.properties} — Hibernate tu choi khoi tao entityManagerFactory:
 * "wrong column type encountered in column [noi_dung_sha256] ... found [char], but
 * expecting [varchar(64)]". Ung dung khong start duoc.
 *
 * <p>Phat hien khi dung stack rehearsal that, khong phai qua bo IT: day la mot lech
 * chi lo ra khi ung dung boot len tren mot database da migrate day du.
 */
@Testcontainers
class V16ReviewImageHashTypeTest {

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
    void doi_cot_hash_sang_varchar_va_chay_lai_khong_doi_gi() throws Exception {
        migrateDen("15");

        try (Connection ket = ketNoi()) {
            assertThat(kieuCot(ket))
                    .as("truoc V16, cot van la CHAR — chinh la kieu lam validate that bai")
                    .isEqualTo("char:64:NO");
        }

        migrateDen("16");

        try (Connection ket = ketNoi()) {
            assertThat(kieuCot(ket))
                    .as("entity map String length=64, tuc Hibernate doi VARCHAR(64) NOT NULL")
                    .isEqualTo("varchar:64:NO");

            long soDong = motSo(ket, "SELECT COUNT(*) FROM `danhgia_hinh_anh`");

            for (String cau : docCacCauLenhV16()) {
                try (Statement st = ket.createStatement()) {
                    st.execute(cau);
                }
            }

            assertThat(kieuCot(ket))
                    .as("chay lai V16 phai la no-op")
                    .isEqualTo("varchar:64:NO");
            assertThat(motSo(ket, "SELECT COUNT(*) FROM `danhgia_hinh_anh`"))
                    .as("V16 chi doi kieu cot, khong duoc cham vao du lieu")
                    .isEqualTo(soDong);
        }
    }

    private String kieuCot(Connection ket) throws SQLException {
        return motDong(ket,
                "SELECT CONCAT(`DATA_TYPE`, ':', `CHARACTER_MAXIMUM_LENGTH`, ':', `IS_NULLABLE`) "
                        + "FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'danhgia_hinh_anh' "
                        + "AND COLUMN_NAME = 'noi_dung_sha256'");
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

    private List<String> docCacCauLenhV16() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(
                "/db/migration/V16__review_image_hash_column_type.sql")) {
            assertThat(in).as("phai tim thay V16 tren classpath").isNotNull();
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
