package com.example.book_be.shared.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Khoa ranh gioi phan quyen cua cac route dang nhap qua provider.
 *
 * Doc truc tiep source vi SecurityConfiguration can ca ung dung khoi dong de kiem tra bang
 * request that, ma duong do dang bi Docker chan. Kiem tra o muc source van bat duoc lop loi
 * nguy hiem nhat: mot matcher rong lam lo them route ngoai y muon.
 */
class SocialEndpointMatcherTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/example/book_be/shared/security/SecurityConfiguration.java");

    private String source() throws IOException {
        return Files.readString(SOURCE, StandardCharsets.UTF_8);
    }

    @Test
    void the_three_public_social_routes_are_declared_exactly() throws IOException {
        String source = source();

        assertThat(source).contains("\"/tai-khoan/oauth/trang-thai\").permitAll()");
        assertThat(source).contains("\"/tai-khoan/oauth/google/start\").permitAll()");
        assertThat(source).contains("\"/tai-khoan/oauth/google/callback\").permitAll()");
    }

    /**
     * A wildcard here would open every future oauth route by accident, including ones that
     * are meant to require an authenticated account, such as identity linking.
     */
    @Test
    void no_wildcard_matcher_opens_the_oauth_namespace() throws IOException {
        String source = source();

        // Chi xet chuoi trong dau nhay: van ban giai thich trong comment cung chua nguyen
        // van cac mau nay, va cam theo comment se bien tai lieu thanh loi test.
        assertThat(source).doesNotContain("\"/tai-khoan/oauth/**\"");
        // `/tai-khoan/**` tron: `/tai-khoan/_proxy-rehearsal/**` da co tu truoc va do mot
        // filter tra 404 dam nhan, nen no khong nam trong pham vi cam nay.
        assertThat(source).doesNotContain("\"/tai-khoan/**\"");
    }

    @Test
    void social_routes_are_read_only_get_endpoints() throws IOException {
        String source = source();

        // Bat dau va callback deu la dieu huong tren thanh dia chi, nen chi GET. Mo them
        // POST se tao ra mot duong khong CSRF-protected vao chinh luong dang nhap.
        assertThat(source).doesNotContain("HttpMethod.POST, \"/tai-khoan/oauth");
        assertThat(source).doesNotContain("HttpMethod.PUT, \"/tai-khoan/oauth");
        assertThat(source).doesNotContain("HttpMethod.DELETE, \"/tai-khoan/oauth");
    }

    /** Fail-closed van phai la dong cuoi cung cua chuoi matcher. */
    @Test
    void the_chain_still_ends_in_deny_all() throws IOException {
        assertThat(source()).contains("anyRequest().denyAll()");
    }
}
