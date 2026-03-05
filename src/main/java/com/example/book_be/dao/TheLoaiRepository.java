package com.example.book_be.dao;

import com.example.book_be.entity.TheLoai;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(path = "the-loai")
public interface TheLoaiRepository extends JpaRepository<TheLoai, Long> {

    @Query("SELECT t.maTheLoai, t.tenTheLoai, COUNT(s) FROM TheLoai t LEFT JOIN t.listSach s GROUP BY t")
    List<Object[]> findAllWithBookCount();
}
