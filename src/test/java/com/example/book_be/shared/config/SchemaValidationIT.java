package com.example.book_be.shared.config;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.nguoidung.domain.AdminBootstrapState;
import com.example.book_be.nguoidung.repository.AdminBootstrapStateRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Giu bat bien: kieu Java cua khoa chinh bang admin_bootstrap_state phai khop kieu cot that.
 *
 * Boi canh: V10 tao `singleton_id` la TINYINT nhung entity tung khai bao Integer. Tren Aiven,
 * ddl-auto=validate tu choi khoi tao entityManagerFactory ("found [tinyint], but expecting
 * [integer]") va lam ca ung dung khong start duoc.
 *
 * Da do bang thuc nghiem tren MySQL 8.0 cua Testcontainers: ddl-auto=validate o day co chay
 * (map vao mot cot khong ton tai thi context fail) nhung KHONG bat lech do rong kieu so nguyen
 * — ca Integer lan Long deu qua duoc du cot la TINYINT. Vi vay khong the dua vao rieng validate
 * de chan lop loi nay; test doi chieu truc tiep information_schema voi metamodel.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class SchemaValidationIT {

    /** Kieu cot khai bao trong V10. Doi migration ma khong doi entity se lam test nay do. */
    private static final String KIEU_COT_SINGLETON_ID = "tinyint";

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private AdminBootstrapStateRepository adminBootstrapStateRepository;

    @Test
    void kieu_khoa_chinh_bootstrap_khop_kieu_cot_that_trong_database() {
        String kieuCotThat = (String) entityManager
                .createNativeQuery("""
                        select DATA_TYPE from information_schema.COLUMNS
                        where TABLE_SCHEMA = database()
                          and TABLE_NAME = 'admin_bootstrap_state'
                          and COLUMN_NAME = 'singleton_id'
                        """)
                .getSingleResult();

        assertThat(kieuCotThat)
                .as("V10 tao singleton_id la TINYINT")
                .isEqualToIgnoringCase(KIEU_COT_SINGLETON_ID);

        EntityType<AdminBootstrapState> entity = entityManager.getMetamodel().entity(AdminBootstrapState.class);
        SingularAttribute<? super AdminBootstrapState, ?> khoaChinh = entity.getId(entity.getIdType().getJavaType());

        assertThat(khoaChinh.getJavaType())
                .as("cot %s phai duoc map bang Byte; Integer/Long se lam Aiven tu choi khoi dong",
                        KIEU_COT_SINGLETON_ID)
                .isEqualTo(Byte.class);
    }

    @Test
    void doc_ghi_duoc_dong_singleton_bootstrap() {
        Optional<AdminBootstrapState> trangThai =
                adminBootstrapStateRepository.findById(AdminBootstrapState.SINGLETON_ID);

        assertThat(trangThai)
                .as("V10 phai tao san dong singleton")
                .isPresent();
        assertThat(trangThai.get().getSingletonId())
                .isEqualTo(AdminBootstrapState.SINGLETON_ID);
    }

    @Test
    void context_khoi_dong_duoc_nghia_la_khong_thieu_bang_hoac_cot() {
        assertThat(entityManager.getEntityManagerFactory().getMetamodel().getEntities())
                .as("ddl-auto=validate da chay va khong bao thieu bang/cot nao")
                .isNotEmpty();
    }
}
