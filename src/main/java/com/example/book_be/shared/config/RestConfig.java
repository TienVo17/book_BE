package com.example.book_be.shared.config;

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

        // Su danh gia: khong con cau hinh o day.
        //
        // Ly do cu ("KHONG dong association exposure de /sach/{id}/listDanhGia van hoat dong
        // vi FE dang dung") da sai tren hai mat. Thu nhat, ham FE goi no —
        // getOneReviewOfOneBook — khong duoc import o dau ca. Thu hai, de ngo association
        // co nghia la danh gia bi admin an van doc duoc cong khai kem co trang thai cu,
        // tuc la thao tac kiem duyet khong co tac dung.
        //
        // Chan collection/item o day cung khong du: /sach/{id}/listDanhGia la association cua
        // Sach, khong phai cua SuDanhGia, nen cau hinh forDomainType(SuDanhGia) khong cham toi
        // no. Cach dung la @RepositoryRestResource(exported = false) tren chinh
        // SuDanhGiaRepository — khi do Spring Data REST khong sinh link nao, ke ca link
        // association tu Sach, va association con lai cua Sach (/sach/{id}/listTheLoai)
        // van tra 200. (/sach/{id}/listHinhAnh da dong san tu truoc vi HinhAnhRepository
        // la exported = false — khong lien quan den thay doi nay.)
        // Kiem chung boi ReviewExposureIT.
    }

    private void blockHttpMethods(Class<?> type, RepositoryRestConfiguration config, HttpMethod[] methods) {
        config.getExposureConfiguration()
                .forDomainType(type)
                .withItemExposure((metadata, httpMethods) -> httpMethods.disable(methods))
                .withCollectionExposure((metadata, httpMethods) -> httpMethods.disable(methods))
                .withAssociationExposure((metadata, httpMethods) -> httpMethods.disable(methods));
    }
}
