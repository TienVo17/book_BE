package com.example.book_be.bo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaseBo {
    private LocalDateTime createDate;
    private LocalDateTime modifyDate;
    private String createBy;
    private String modifyBy;
    private Integer page = 0;
    private Integer pageSize = 10;
}
