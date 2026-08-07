package com.example.book_be.sach.dto;
import com.example.book_be.shared.dto.BaseBo;

import lombok.Data;

@Data
public class SachBo extends BaseBo{
    private Boolean isAdmin;
    private String tenSach;
    private Integer maTheLoai;
    private Double giaMin;
    private Double giaMax;
    /** Da duoc controller validate; gia tri hop le la mot trong cac hang so SORT_* o SachService. */
    private String sort;
}
