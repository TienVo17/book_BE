package com.example.book_be.services.admin;

import com.example.book_be.bo.SachBo;
import com.example.book_be.dao.HinhAnhRepository;
import com.example.book_be.dao.SachRepository;
import com.example.book_be.entity.HinhAnh;
import com.example.book_be.entity.Sach;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SachServiceImpl implements SachService {
    private final ObjectMapper objectMapper;
    @Autowired
    private SachRepository sachRepository;

    @Autowired
    private HinhAnhRepository hinhAnhRepository;

    public SachServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    @Override
    public Page<Sach> findBookByName(String tenSach, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return sachRepository.findByTenSachContaining(tenSach, pageable);
    }
    @Override
    public Page<Sach> findAll(SachBo model) {
        Pageable pageable = PageRequest.of(model.getPage(), model.getPageSize());
        Page<Sach> sachPage = sachRepository.findAll((root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if(Boolean.FALSE.equals(model.getIsAdmin())){
                predicates.add(builder.equal(root.get("isActive"),1));
            }
            query.orderBy(builder.desc(root.get("maSach")));
            return builder.and(predicates.toArray(new Predicate[0]));
        }, pageable);
        sachPage.getContent().forEach(sach -> {
            List<HinhAnh> hinhAnhList = hinhAnhRepository.findAll((root, query, builder) -> builder.equal(
                    root.get("sach").get("maSach"), sach.getMaSach()
            ));
            sach.setListHinhAnh(hinhAnhList);
        });
        return sachPage;
    }

    @Override
    public Sach save(Sach sach) {
        Sach bo = sachRepository.save(sach);
        if (!bo.getListImageStr().isEmpty()) {
            for (String urlString : bo.getListImageStr()) {
                HinhAnh hinhAnh = new HinhAnh();
                hinhAnh.setTenHinhAnh("img" + bo.getTenSach());
                hinhAnh.setUrlHinh(urlString);
                hinhAnh.setIcon(false);
                hinhAnh.setSach(bo);
                hinhAnhRepository.save(hinhAnh);
            }
        }
        return bo;
    }

    @Override
    public Sach update(Sach bo) throws Exception {
        Sach sach = sachRepository.findById(Long.valueOf(bo.getMaSach())).orElse(null);
        if (sach == null) {
            throw new Exception("");
        }
        sach = objectMapper.convertValue(bo, Sach.class);
        Sach finalSach = sach;
        List<HinhAnh> hinhAnhList = hinhAnhRepository.findAll((root, query, builder) -> builder.equal(
                root.get("sach").get("maSach"), finalSach.getMaSach()
        ));
        if (hinhAnhList.size() > 0) {
            hinhAnhRepository.deleteAll(hinhAnhList);
        }
        if (!bo.getListImageStr().isEmpty()) {
            for (String urlString : bo.getListImageStr()) {
                HinhAnh hinhAnh = new HinhAnh();
                hinhAnh.setTenHinhAnh("img" + bo.getTenSach());
                hinhAnh.setUrlHinh(urlString);
                hinhAnh.setIcon(false);
                hinhAnh.setSach(sach);
                hinhAnhRepository.save(hinhAnh);
            }
        }
        return sachRepository.save(sach);
    }

    @Override
    public Sach delete(Long id) {
        return null;
    }

    @Override
    public Sach findById(Long id) {
        return null;
    }

    @Override
    public Sach active(Long id) {
        Sach sach = sachRepository.findById(id).orElse(null);
        sach.setIsActive(1);
        sachRepository.save(sach);
        return sach;
    }

    @Override
    public Sach unactive(Long id) {
        Sach sach = sachRepository.findById(id).orElse(null);
        sach.setIsActive(0);
        sachRepository.save(sach);
        return sach;
    }
}
