package com.example.book_be.danhgia.web;

import com.example.book_be.danhgia.service.DanhGiaDocService;
import com.example.book_be.danhgia.service.DanhGiaHinhAnhService;
import com.example.book_be.danhgia.service.DanhGiaService;
import com.example.book_be.nguoidung.baomat.JwtService;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.service.UserService;
import com.example.book_be.shared.web.ApiExceptionHandler;
import com.example.book_be.shared.web.ApiErrorWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DanhGiaController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, ApiErrorWriter.class})
class ReviewImageControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    DanhGiaService danhGiaService;
    @MockBean
    DanhGiaDocService danhGiaDocService;
    @MockBean
    DanhGiaHinhAnhService danhGiaHinhAnhService;
    @MockBean
    NguoiDungRepository nguoiDungRepository;
    @MockBean
    JwtService jwtService;
    @MockBean
    UserService userService;

    @Test
    void thieu_part_tep_tra_400_theo_api_error_thay_vi_500() throws Exception {
        mvc.perform(multipart("/api/danh-gia/{maDanhGia}/hinh-anh", 11L)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/danh-gia/11/hinh-anh"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
