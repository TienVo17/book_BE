package com.example.book_be.nguoidung.service;

import com.example.book_be.nguoidung.dto.PhanQuyenBo;
import com.example.book_be.nguoidung.dto.UserBo;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.session.RefreshSessionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    @Autowired
    private NguoiDungRepository nguoiDungRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private RefreshSessionService refreshSessionService;

    @Override
    public Page<NguoiDung> findAll(UserBo model) {
        Pageable pageable = PageRequest.of(model.getPage(), model.getPageSize());
        return nguoiDungRepository.findAll((root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            return builder.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    @Override
    public NguoiDung save(UserBo bo) {
        return null;
    }

    @Override
    public NguoiDung update(UserBo bo) {
        return null;
    }

    @Override
    public NguoiDung delete(Long id) {
        return null;
    }

    @Override
    public NguoiDung findById(Long id) {
        return null;
    }

    @Transactional
    @Override
    public void phanQuyen(PhanQuyenBo phanQuyenBo) {
        nguoiDungRepository.findByIdForAuthWrite(phanQuyenBo.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));
        String sqlDelete = "DELETE FROM nguoidung_quyen WHERE ma_nguoi_dung = :maNguoiDung";
        Query deleteQuery = entityManager.createNativeQuery(sqlDelete);
        deleteQuery.setParameter("maNguoiDung", phanQuyenBo.getUserId());
        deleteQuery.executeUpdate();

        for (Integer roleId : phanQuyenBo.getQuyenIds()) {
            String sql = "INSERT INTO nguoidung_quyen (ma_nguoi_dung, ma_quyen) VALUES (:maNguoiDung, :maQuyen)";
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("maNguoiDung",  phanQuyenBo.getUserId()); // ID người dùng
            query.setParameter("maQuyen", roleId); // ID quyền
            query.executeUpdate();
        }
        refreshSessionService.revokeAllByUser(phanQuyenBo.getUserId());
    }
}
