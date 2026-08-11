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
class V19ServerCartIntegrityTest {

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
    void migrate_tu_v18_gop_duplicate_xoa_quantity_loi_va_siet_contract() throws Exception {
        migrateDen("18");

        try (Connection ket = ketNoi()) {
            long maNguoiDung = motSo(ket, "SELECT MIN(`ma_nguoi_dung`) FROM `nguoi_dung`");
            long maSachA = motSo(ket, "SELECT MIN(`ma_sach`) FROM `sach`");
            long maSachB = motSo(ket, "SELECT MIN(`ma_sach`) FROM `sach` WHERE `ma_sach` > " + maSachA);

            chenGioHang(ket, maNguoiDung, maSachA, "5");
            chenGioHang(ket, maNguoiDung, maSachA, "7");
            chenGioHang(ket, maNguoiDung, maSachA, "NULL");
            chenGioHang(ket, maNguoiDung, maSachA, "0");
            chenGioHang(ket, maNguoiDung, maSachB, "2147483647");
            chenGioHang(ket, maNguoiDung, maSachB, "1");

            migrateDen("19");

            assertThat(motSo(ket, "SELECT COUNT(*) FROM `gio_hang` WHERE `ma_nguoi_dung` = "
                    + maNguoiDung)).isEqualTo(2);
            assertThat(motSo(ket, "SELECT `so_luong` FROM `gio_hang` WHERE `ma_nguoi_dung` = "
                    + maNguoiDung + " AND `ma_sach` = " + maSachA)).isEqualTo(12);
            assertThat(motSo(ket, "SELECT `so_luong` FROM `gio_hang` WHERE `ma_nguoi_dung` = "
                    + maNguoiDung + " AND `ma_sach` = " + maSachB)).isEqualTo(Integer.MAX_VALUE);

            assertThat(motDong(ket, "SELECT GROUP_CONCAT(`COLUMN_NAME` ORDER BY `SEQ_IN_INDEX`) "
                    + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'gio_hang' AND INDEX_NAME = 'uk_gio_hang_nguoi_sach' "
                    + "AND NON_UNIQUE = 0"))
                    .isEqualTo("ma_nguoi_dung,ma_sach");
            assertThat(motDong(ket, "SELECT CONCAT(`IS_NULLABLE`, ':', `DATA_TYPE`) "
                    + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'gio_hang' AND COLUMN_NAME = 'so_luong'"))
                    .isEqualTo("NO:int");
            assertThat(motSo(ket, "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gio_hang' "
                    + "AND CONSTRAINT_NAME = 'chk_gio_hang_so_luong_duong' "
                    + "AND CONSTRAINT_TYPE = 'CHECK'")).isOne();

            assertThat(statementBiTuChoi(ket, "INSERT INTO `gio_hang` (`so_luong`, `ma_sach`, `ma_nguoi_dung`) "
                    + "VALUES (1, " + maSachA + ", " + maNguoiDung + ")"))
                    .as("unique user-book phai chan duplicate row").isTrue();
            assertThat(statementBiTuChoi(ket, "UPDATE `gio_hang` SET `so_luong` = 0 "
                    + "WHERE `ma_nguoi_dung` = " + maNguoiDung + " AND `ma_sach` = " + maSachB))
                    .as("check quantity phai chan zero").isTrue();

            assertThat(motDong(ket, "SELECT GROUP_CONCAT(`COLUMN_NAME` ORDER BY `SEQ_IN_INDEX`) "
                    + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'gio_hang_merge_receipt' "
                    + "AND INDEX_NAME = 'uk_gio_hang_merge_nguoi_key' AND NON_UNIQUE = 0"))
                    .isEqualTo("ma_nguoi_dung,idempotency_key");
            assertThat(motDong(ket, "SELECT GROUP_CONCAT(`COLUMN_NAME` ORDER BY `SEQ_IN_INDEX`) "
                    + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'gio_hang_merge_receipt' "
                    + "AND INDEX_NAME = 'idx_gio_hang_merge_created_id'"))
                    .isEqualTo("created_at,ma_gio_hang_merge_receipt");
            assertThat(motSo(ket, "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gio_hang_merge_receipt' "
                    + "AND CONSTRAINT_TYPE = 'PRIMARY KEY'")).isOne();
            assertThat(motDong(ket, "SELECT CONCAT(`IS_NULLABLE`, ':', `DATA_TYPE`, ':', `CHARACTER_MAXIMUM_LENGTH`) "
                    + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'gio_hang_merge_receipt' "
                    + "AND COLUMN_NAME = 'request_fingerprint'"))
                    .isEqualTo("NO:varchar:64");
            assertThat(motDong(ket, "SELECT `DELETE_RULE` FROM information_schema.REFERENTIAL_CONSTRAINTS "
                    + "WHERE CONSTRAINT_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'gio_hang_merge_receipt' "
                    + "AND CONSTRAINT_NAME = 'fk_gio_hang_merge_receipt_nguoi_dung'"))
                    .isEqualTo("CASCADE");
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

    private void chenGioHang(Connection ket, long maNguoiDung, long maSach, String soLuong)
            throws SQLException {
        try (Statement st = ket.createStatement()) {
            st.executeUpdate("INSERT INTO `gio_hang` (`so_luong`, `ma_sach`, `ma_nguoi_dung`) VALUES ("
                    + soLuong + ", " + maSach + ", " + maNguoiDung + ")");
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
