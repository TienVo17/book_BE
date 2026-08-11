package com.example.book_be.giohang.web;

import com.example.book_be.giohang.dto.CartMergeResponse;
import com.example.book_be.giohang.service.CartService;
import com.example.book_be.nguoidung.baomat.JwtService;
import com.example.book_be.nguoidung.service.UserService;
import com.example.book_be.shared.web.ApiErrorWriter;
import com.example.book_be.shared.web.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GioHangController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, ApiErrorWriter.class})
class GioHangControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    CartService cartService;
    @MockBean
    JwtService jwtService;
    @MockBean
    UserService userService;

    @Test
    void merge_chuyen_idempotency_key_xuong_service() throws Exception {
        when(cartService.mergeGuestCart(any(), any())).thenReturn(new CartMergeResponse());

        mvc.perform(post("/api/gio-hang/merge")
                        .header("Idempotency-Key", "merge-key-123")
                        .contentType("application/json")
                        .content("{\"items\":[{\"maSach\":1,\"soLuong\":2}]}"))
                .andExpect(status().isOk());

        verify(cartService).mergeGuestCart(any(), org.mockito.ArgumentMatchers.eq("merge-key-123"));
    }

    @Test
    void merge_thieu_idempotency_key_tra_400_truoc_service() throws Exception {
        mvc.perform(post("/api/gio-hang/merge")
                        .contentType("application/json")
                        .content("{\"items\":[{\"maSach\":1,\"soLuong\":2}]}"))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(cartService);
    }

    @Test
    void update_quantity_body_null_tra_400_thay_vi_500() throws Exception {
        when(cartService.updateItemQuantity(1, null))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Thiếu số lượng."));

        mvc.perform(put("/api/gio-hang/items/1")
                        .contentType("application/json")
                        .content("null"))
                .andExpect(status().isBadRequest());

        verify(cartService).updateItemQuantity(1, null);
    }

    @Test
    void merge_body_null_tra_400_va_khong_tieu_idempotency_key() throws Exception {
        when(cartService.mergeGuestCart(null, "unused-key"))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Danh sách sản phẩm merge không được để trống."));

        mvc.perform(post("/api/gio-hang/merge")
                        .header("Idempotency-Key", "unused-key")
                        .contentType("application/json")
                        .content("null"))
                .andExpect(status().isBadRequest());

        verify(cartService).mergeGuestCart(null, "unused-key");
    }
}
