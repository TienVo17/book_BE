package com.example.book_be.yeuthich.web;

import com.example.book_be.nguoidung.baomat.JwtService;
import com.example.book_be.nguoidung.service.UserService;
import com.example.book_be.shared.web.ApiErrorWriter;
import com.example.book_be.shared.web.ApiExceptionHandler;
import com.example.book_be.yeuthich.dto.WishlistItemResponse;
import com.example.book_be.yeuthich.service.WishlistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(YeuThichController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, ApiErrorWriter.class})
class YeuThichControllerTest {

    @Autowired MockMvc mvc;
    @MockBean WishlistService wishlistService;
    @MockBean JwtService jwtService;
    @MockBean UserService userService;

    @Test
    void get_tra_flat_dto_khong_lo_raw_entity_graph() throws Exception {
        when(wishlistService.getCurrentUserWishlist()).thenReturn(List.of(
                new WishlistItemResponse(3, "Sách", 75000D, "https://image.example/book.jpg")
        ));

        mvc.perform(get("/api/yeu-thich"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].maSach").value(3))
                .andExpect(jsonPath("$[0].tenSach").value("Sách"))
                .andExpect(jsonPath("$[0].giaBan").value(75000D))
                .andExpect(jsonPath("$[0].hinhAnh").value("https://image.example/book.jpg"))
                .andExpect(jsonPath("$[0].nguoiDung").doesNotExist())
                .andExpect(jsonPath("$[0].sach").doesNotExist());
    }

    @Test
    void post_va_delete_giu_endpoint_cu_nhung_tra_snapshot_authoritative() throws Exception {
        List<WishlistItemResponse> added = List.of(
                new WishlistItemResponse(3, "Sách", 75000D, "")
        );
        when(wishlistService.ensureBookPresent(3)).thenReturn(added);
        when(wishlistService.ensureBookAbsent(3)).thenReturn(List.of());

        mvc.perform(post("/api/yeu-thich/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].maSach").value(3));
        mvc.perform(delete("/api/yeu-thich/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(wishlistService).ensureBookPresent(3);
        verify(wishlistService).ensureBookAbsent(3);
    }
}
