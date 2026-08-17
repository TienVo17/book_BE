package com.example.book_be.shared.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Path CSRF_FILTER_SOURCE = Path.of(
            "src/main/java/com/example/book_be/nguoidung/session/AuthOriginCsrfFilter.java");

    private String source() throws IOException {
        return Files.readString(SOURCE, StandardCharsets.UTF_8);
    }

    @Test
    void every_public_social_route_is_declared_exactly() throws IOException {
        String source = source();

        assertThat(source).contains("\"/tai-khoan/oauth/trang-thai\").permitAll()");
        assertThat(source).contains("\"/tai-khoan/oauth/google/start\").permitAll()");
        assertThat(source).contains("\"/tai-khoan/oauth/google/callback\").permitAll()");
        assertThat(source).contains("\"/tai-khoan/oauth/facebook/start\").permitAll()");
        assertThat(source).contains("\"/tai-khoan/oauth/facebook/callback\").permitAll()");
        // Buoc hoan tat chay khi chua co phien: nguoi dung moi co ho so dang do, chua co
        // tai khoan. Cookie ho so la thu duy nhat cho phep goi duong nay.
        assertThat(source).contains("\"/tai-khoan/oauth/dang-ky-cho\").permitAll()");
    }

    /**
     * The controller maps {provider}, so a path-variable matcher here would open any provider
     * name someone can invent, including ones with no configuration behind them.
     */
    @Test
    void no_path_variable_matcher_stands_in_for_a_provider_name() throws IOException {
        String source = source();

        assertThat(source).doesNotContain("\"/tai-khoan/oauth/*/start\"");
        assertThat(source).doesNotContain("\"/tai-khoan/oauth/*/callback\"");
        assertThat(source).doesNotContain("\"/tai-khoan/oauth/{provider}");
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

    /** Bat dau va callback deu la dieu huong tren thanh dia chi, nen chi GET. */
    @Test
    void provider_start_and_callback_stay_get_only() throws IOException {
        String source = source();

        assertThat(source).doesNotContain("HttpMethod.POST, \"/tai-khoan/oauth/google");
        assertThat(source).doesNotContain("HttpMethod.POST, \"/tai-khoan/oauth/facebook");
        assertThat(source).doesNotContain("HttpMethod.PUT, \"/tai-khoan/oauth");
        assertThat(source).doesNotContain("HttpMethod.DELETE, \"/tai-khoan/oauth");
    }

    /**
     * Buoc hoan tat dang ky bat buoc phai la POST, nen lenh cam POST tron truoc day khong con
     * dung nua. Dieu that su can giu la ly do cua lenh cam do: mot POST mo ma khong qua
     * Origin/CSRF la mot duong vao khong duoc bao ve cua chinh luong dang nhap.
     *
     * Nen thay vi cam, o day liet ke chinh xac va doi chieu sang danh sach cua
     * AuthOriginCsrfFilter. Them mot POST moi ma quen bao ve se lam test nay do.
     */
    @Test
    void every_public_social_post_route_is_csrf_protected() throws IOException {
        String source = source();
        String filter = Files.readString(CSRF_FILTER_SOURCE, StandardCharsets.UTF_8);

        List<String> postPaths = new ArrayList<>();
        Matcher matcher = Pattern
                .compile("HttpMethod\\.POST, \"(/tai-khoan/oauth/[^\"]+)\"")
                .matcher(source);
        while (matcher.find()) {
            postPaths.add(matcher.group(1));
        }

        assertThat(postPaths).containsExactlyInAnyOrder(
                "/tai-khoan/oauth/gui-ma-xac-minh-email",
                "/tai-khoan/oauth/xac-minh-email",
                "/tai-khoan/oauth/hoan-tat-dang-ky");
        for (String path : postPaths) {
            assertThat(filter).contains("\"" + path + "\"");
        }
    }

    /** Fail-closed van phai la dong cuoi cung cua chuoi matcher. */
    @Test
    void the_chain_still_ends_in_deny_all() throws IOException {
        assertThat(source()).contains("anyRequest().denyAll()");
    }
}
