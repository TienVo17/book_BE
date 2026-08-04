package com.example.book_be.shared.config;

import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.sach.domain.Sach;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurer;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

@Component
public class RestConfig implements RepositoryRestConfigurer {

    private static final HttpMethod[] ALL_HTTP_METHODS = {
            HttpMethod.GET,
            HttpMethod.POST,
            HttpMethod.PUT,
            HttpMethod.PATCH,
            HttpMethod.DELETE
    };

    @Autowired
    private EntityManager entityManager;

    @Override
    public void configureRepositoryRestConfiguration(RepositoryRestConfiguration config, CorsRegistry cors) {
        config.exposeIdsFor(entityManager.getMetamodel().getEntities().stream()
                .map(Type::getJavaType)
                .toArray(Class[]::new));

        HttpMethod[] disableSachMutationMethods = {
                HttpMethod.POST,
                HttpMethod.PUT,
                HttpMethod.PATCH,
                HttpMethod.DELETE
        };
        blockHttpMethods(Sach.class, config, disableSachMutationMethods);

        // Nguoi dung: dong hoan toan collection/item/association qua Spring Data REST.
        // Cac endpoint search (existsByTenDangNhap/existsByEmail) khong bi anh huong boi
        // config nay nen 2 endpoint public van hoat dong (xac nhan boi AdminAndRepositoryExposureIT).
        blockHttpMethods(NguoiDung.class, config, ALL_HTTP_METHODS);

        // Su danh gia (danhgia): dong collection/item rieng cua repository nay, nhung KHONG dong
        // association exposure de /sach/{id}/listDanhGia (quan he doc-only tu Sach, FE dang dung)
        // van hoat dong binh thuong.
        blockCollectionAndItem(SuDanhGia.class, config, ALL_HTTP_METHODS);
    }

    private void blockHttpMethods(Class<?> type, RepositoryRestConfiguration config, HttpMethod[] methods) {
        config.getExposureConfiguration()
                .forDomainType(type)
                .withItemExposure((metadata, httpMethods) -> httpMethods.disable(methods))
                .withCollectionExposure((metadata, httpMethods) -> httpMethods.disable(methods))
                .withAssociationExposure((metadata, httpMethods) -> httpMethods.disable(methods));
    }

    private void blockCollectionAndItem(Class<?> type, RepositoryRestConfiguration config, HttpMethod[] methods) {
        config.getExposureConfiguration()
                .forDomainType(type)
                .withItemExposure((metadata, httpMethods) -> httpMethods.disable(methods))
                .withCollectionExposure((metadata, httpMethods) -> httpMethods.disable(methods));
    }
}
