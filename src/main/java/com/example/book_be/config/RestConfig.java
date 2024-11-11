package com.example.book_be.config;

import com.example.book_be.entity.NguoiDung;
import com.example.book_be.entity.TheLoai;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurer;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

@Component
public class RestConfig implements RepositoryRestConfigurer {

    @Autowired
    private EntityManager entityManager;
    private String url = "http://localhost:3000";

    @Override
    public void configureRepositoryRestConfiguration(RepositoryRestConfiguration config, CorsRegistry cors) {
        // Cấu hình cho phép trả về id của entity khi gọi endpoint get
        config.exposeIdsFor(entityManager.getMetamodel().getEntities().stream()
                .map(Type::getJavaType)
                .toArray(Class[]::new));

        // Cấu hình CORS, cho phép các nguồn khác truy cập với các phương thức HTTP cụ thể
//        cors.addMapping("/**")
//                .allowedOrigins(front_end_host)
//                .allowedMethods("GET", "POST", "PUT", "DELETE");

        // Cấu hình CORS, cho phép tất cả các nguồn khác truy cập với tất cả các phương thức HTTP
//        cors.addMapping("/**");

        // Cấu hình CORS, chặn tất cả các nguồn khác truy cập
//        cors.addMapping("/**")
//                .allowedOrigins("http://localhost:3000")
//                .allowedMethods("GET", "POST", "PUT", "DELETE");

        // Cấu hình CORS, chặn tất cả các nguồn khác truy cập
//        cors.addMapping("/**")
//                .allowedOrigins(url)
//                .allowedMethods("GET", "POST", "PUT", "DELETE");

        // Cấu hình CORS, cho phép tất cả các nguồn khác truy cập với tất cả các phương thức HTTP
        cors.addMapping("/**");

        // Cấu hình CORS, cho phép các nguồn khác truy cập với các phương thức HTTP cụ thể
//        cors.addMapping("/**")
//                .allowedOrigins(url)  // Thay bằng URL frontend của bạn
//                .allowedMethods("GET", "POST", "PUT", "DELETE");

        // Chặn các phương thức HTTP cho các entity cụ thể
//        HttpMethod[] disableMethods = {
//                HttpMethod.POST,
//                HttpMethod.PUT,
//                HttpMethod.PATCH,
//                HttpMethod.DELETE,
//        };
//
//        blockHttpMethods(TheLoai.class, config, disableMethods);  // Ví dụ chặn cho entity 'TheLoai'
//        blockHttpMethods(NguoiDung.class, config, new HttpMethod[]{HttpMethod.DELETE});  // Ví dụ chặn DELETE cho 'NguoiDung'
    }

    // Phương thức hỗ trợ chặn các phương thức HTTP cho các entity cụ thể
    private void blockHttpMethods(Class<?> c, RepositoryRestConfiguration config, HttpMethod[] methods) {
        config.getExposureConfiguration()
                .forDomainType(c)
                .withItemExposure((metadata, httpMethods) -> httpMethods.disable(methods))
                .withCollectionExposure((metadata, httpMethods) -> httpMethods.disable(methods));
    }
}
