package com.example.book_be.nguoidung.dto;

import lombok.Data;

import java.util.List;

@Data
public class PhanQuyenBo {
    List<Integer> quyenIds;
    Integer userId;
}
