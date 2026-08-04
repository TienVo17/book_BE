package com.example.book_be.nguoidung.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Chay bootstrap admin luc khoi dong khi va chi khi duoc bat tuong minh.
 * Chi log dinh danh va ket qua — khong bao gio log mat khau hoac hash.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminBootstrapProperties properties;
    private final AdminBootstrapService adminBootstrapService;

    public AdminBootstrapRunner(AdminBootstrapProperties properties,
                                AdminBootstrapService adminBootstrapService) {
        this.properties = properties;
        this.adminBootstrapService = adminBootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }

        AdminBootstrapRequest yeuCau = properties.yeuCauHopLe();
        boolean daTao = adminBootstrapService.taoAdminNeuChuaSuDung(yeuCau);

        if (daTao) {
            LOGGER.info("event=admin_bootstrap result=created username={}", yeuCau.tenDangNhap());
            LOGGER.warn("event=admin_bootstrap action_required=Xoa ADMIN_BOOTSTRAP_* khoi moi truong va khoi dong lai");
        } else {
            LOGGER.info("event=admin_bootstrap result=already_consumed");
        }
    }
}
