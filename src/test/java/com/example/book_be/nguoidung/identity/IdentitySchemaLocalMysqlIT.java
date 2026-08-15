package com.example.book_be.nguoidung.identity;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chay V22/V23 tren MySQL that va doi chieu voi entity.
 *
 * Ly do ton tai: production dat ddl-auto=validate, nen mot cot lech kieu giua migration va
 * entity se lam backend KHONG khoi dong duoc sau khi deploy — chu khong phai lam hong mot
 * tinh nang. Testcontainers dang bi Docker Engine 29 chan, nen dung MySQL cuc bo theo dung
 * cach RefreshTokenSessionLocalMysqlIT da dung o Release 1.
 *
 * Chay bang:
 *   $env:IDENTITY_SCHEMA_MYSQL_IT="true"
 *   $env:IDENTITY_IT_DB_URL="jdbc:mysql://localhost:3306/web_ban_sach_r2_test"
 *   $env:IDENTITY_IT_DB_USERNAME="root"
 *   $env:IDENTITY_IT_DB_PASSWORD="<mat khau cuc bo>"
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=${IDENTITY_IT_DB_URL}",
                "spring.datasource.username=${IDENTITY_IT_DB_USERNAME}",
                "spring.datasource.password=${IDENTITY_IT_DB_PASSWORD:}",
                "jwt.secret=local-integration-only-secret-value-32b",
                "app.auth.refresh-enabled=false",
                "app.auth.google-enabled=false",
                "app.auth.facebook-enabled=false",
        })
@EnabledIfEnvironmentVariable(named = "IDENTITY_SCHEMA_MYSQL_IT", matches = "true")
@Import(IdentitySchemaLocalMysqlIT.MailTestConfiguration.class)
class IdentitySchemaLocalMysqlIT {
    private static final String DEDICATED_SCHEMA = "web_ban_sach_r2_test";

    /**
     * Test nay chi kiem tra schema, nhung context that can mot JavaMailSender. Dung ban gia
     * de khong bao gio gui thu that tu mot lan chay kiem tra.
     */
    @TestConfiguration
    static class MailTestConfiguration {
        @Bean
        JavaMailSender javaMailSender() {
            return new org.springframework.mail.javamail.JavaMailSenderImpl() {
                @Override
                public MimeMessage createMimeMessage() {
                    return new MimeMessage(Session.getInstance(new java.util.Properties()));
                }

                @Override
                public void send(MimeMessage... mimeMessages) {
                    throw new UnsupportedOperationException("Schema check must never send mail");
                }
            };
        }
    }

    @DynamicPropertySource
    static void requireDedicatedSchema(DynamicPropertyRegistry registry) {
        String url = System.getenv("IDENTITY_IT_DB_URL");
        // Chan tuyet doi viec tro nham vao schema dev/production: test nay chay migration.
        if (url == null || !url.contains(DEDICATED_SCHEMA)) {
            throw new IllegalStateException(
                    "IDENTITY_IT_DB_URL must point at the dedicated schema " + DEDICATED_SCHEMA);
        }
    }

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Context khoi dong duoc nghia la Flyway da chay het V1..V23 va ddl-auto=validate chap
     * nhan toan bo entity, ke ca AuthIdentity/OAuthTransaction moi them.
     */
    @Test
    void migrations_apply_and_entities_validate_against_the_real_schema() {
        assertThat(entityManager.getEntityManagerFactory().getMetamodel().getEntities()).isNotEmpty();
    }

    @Test
    void auth_identity_is_unique_on_provider_issuer_and_subject() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                select INDEX_NAME, GROUP_CONCAT(COLUMN_NAME order by SEQ_IN_INDEX)
                from information_schema.STATISTICS
                where TABLE_SCHEMA = database() and TABLE_NAME = 'auth_identity'
                  and NON_UNIQUE = 0 and INDEX_NAME <> 'PRIMARY'
                group by INDEX_NAME
                """).getResultList();

        assertThat(rows).anySatisfy(row ->
                assertThat((String) row[1]).isEqualTo("provider,issuer,provider_subject"));
    }

    @Test
    void oauth_transaction_is_unique_on_state_hash() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                select INDEX_NAME, GROUP_CONCAT(COLUMN_NAME order by SEQ_IN_INDEX)
                from information_schema.STATISTICS
                where TABLE_SCHEMA = database() and TABLE_NAME = 'oauth_transaction'
                  and NON_UNIQUE = 0 and INDEX_NAME <> 'PRIMARY'
                group by INDEX_NAME
                """).getResultList();

        assertThat(rows).anySatisfy(row -> assertThat((String) row[1]).isEqualTo("state_hash"));
    }

    /** Khoa ngoai phai CASCADE de xoa nguoi dung khong de lai danh tinh mo coi. */
    @Test
    void auth_identity_cascades_when_its_user_is_deleted() {
        Object rule = entityManager.createNativeQuery("""
                select DELETE_RULE from information_schema.REFERENTIAL_CONSTRAINTS
                where CONSTRAINT_SCHEMA = database()
                  and CONSTRAINT_NAME = 'fk_auth_identity_nguoi_dung'
                """).getSingleResult();

        assertThat((String) rule).isEqualToIgnoringCase("CASCADE");
    }
}
