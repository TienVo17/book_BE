package com.example.book_be.sach.dto;
import com.example.book_be.bo.BaseBo;

import lombok.Data;

@Data
public class SachBo extends BaseBo{
    private Boolean isAdmin;
    private String tenSach;
    private Integer maTheLoai;
}
