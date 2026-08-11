package com.example.book_be.shared.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class V20WishlistIntegrityTest {

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
    void migrate_tu_v19_gop_duplicate_va_siet_unique_user_book() throws Exception {
        migrateDen("19");

        try (Connection ket = ketNoi()) {
            long maNguoiDung = motSo(ket, "SELECT MIN(`ma_nguoi_dung`) FROM `nguoi_dung`");
            long maSach = motSo(ket, "SELECT MIN(`ma_sach`) FROM `sach`");
            long firstId = chenYeuThich(ket, maNguoiDung, maSach);
            chenYeuThich(ket, maNguoiDung, maSach);
            chenYeuThich(ket, maNguoiDung, maSach);

            migrateDen("20");

            assertThat(motSo(ket, "SELECT COUNT(*) FROM `sach_yeu_thich` WHERE `ma_nguoi_dung` = "
                    + maNguoiDung + " AND `ma_sach` = " + maSach)).isOne();
            assertThat(motSo(ket, "SELECT `ma_sach_yeu_thich` FROM `sach_yeu_thich` "
                    + "WHERE `ma_nguoi_dung` = " + maNguoiDung + " AND `ma_sach` = " + maSach))
                    .as("migration phai giu row cu nhat")
                    .isEqualTo(firstId);
            assertThat(motDong(ket, "SELECT GROUP_CONCAT(`COLUMN_NAME` ORDER BY `SEQ_IN_INDEX`) "
                    + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'sach_yeu_thich' "
                    + "AND INDEX_NAME = 'uk_sach_yeu_thich_nguoi_sach' AND NON_UNIQUE = 0"))
                    .isEqualTo("ma_nguoi_dung,ma_sach");
            assertThat(statementBiTuChoi(ket, "INSERT INTO `sach_yeu_thich` "
                    + "(`ma_nguoi_dung`, `ma_sach`) VALUES (" + maNguoiDung + ", " + maSach + ")"))
                    .as("unique user-book phai chan duplicate row")
                    .isTrue();
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
        return DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private long chenYeuThich(Connection ket, long maNguoiDung, long maSach) throws SQLException {
        try (Statement st = ket.createStatement()) {
            st.executeUpdate("INSERT INTO `sach_yeu_thich` (`ma_nguoi_dung`, `ma_sach`) VALUES ("
                    + maNguoiDung + ", " + maSach + ")", Statement.RETURN_GENERATED_KEYS);
            try (ResultSet keys = st.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            }
        }
    }

    private boolean statementBiTuChoi(Connection ket, String sql) throws SQLException {
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
}
