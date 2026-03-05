package com.example.book_be.services.admin;

import com.example.book_be.bo.SachBo;
import com.example.book_be.dao.HinhAnhRepository;
import com.example.book_be.dao.SachRepository;
import com.example.book_be.entity.HinhAnh;
import com.example.book_be.entity.Sach;
import com.example.book_be.entity.TheLoai;
import com.example.book_be.util.SlugUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
            if (Boolean.FALSE.equals(model.getIsAdmin())) {
                predicates.add(builder.equal(builder.coalesce(root.get("isActive"), 1), 1));
            }
            if (model.getTenSach() != null && !model.getTenSach().isEmpty()) {
                String keyword = "%" + model.getTenSach().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("tenSach")), keyword),
                        builder.like(builder.lower(root.get("tenTacGia")), keyword)
                ));
            }
            if (model.getMaTheLoai() != null && model.getMaTheLoai() > 0) {
                predicates.add(builder.equal(
                        root.join("listTheLoai").get("maTheLoai"), model.getMaTheLoai()
                ));
            }
            query.orderBy(builder.desc(root.get("maSach")));
            return builder.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        sachPage.getContent().forEach(sach -> {
            List<HinhAnh> hinhAnhList = hinhAnhRepository.findAll((root, query, builder) ->
                    builder.equal(root.get("sach").get("maSach"), sach.getMaSach())
            );
            sach.setListHinhAnh(hinhAnhList);
        });
        return sachPage;
    }

    @Override
    public Sach save(Sach sach) {
        // Save first to get generated ID, then assign slug if missing
        Sach bo = sachRepository.save(sach);
        generateSlugIfMissing(bo);
        sachRepository.save(bo);

        if (bo.getListImageStr() != null && !bo.getListImageStr().isEmpty()) {
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
            throw new Exception("Sach not found");
        }
        sach = objectMapper.convertValue(bo, Sach.class);
        generateSlugIfMissing(sach);

        Sach finalSach = sach;
        List<HinhAnh> hinhAnhList = hinhAnhRepository.findAll((root, query, builder) ->
                builder.equal(root.get("sach").get("maSach"), finalSach.getMaSach())
        );
        if (!hinhAnhList.isEmpty()) {
            hinhAnhRepository.deleteAll(hinhAnhList);
        }
        if (bo.getListImageStr() != null && !bo.getListImageStr().isEmpty()) {
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
        Sach sach = sachRepository.findById(id).orElse(null);
        if (sach != null) {
            List<HinhAnh> hinhAnhList = hinhAnhRepository.findAll((root, query, builder) ->
                    builder.equal(root.get("sach").get("maSach"), sach.getMaSach())
            );
            sach.setListHinhAnh(hinhAnhList);
        }
        return sach;
    }

    @Override
    public Sach active(Long id) {
        Sach sach = sachRepository.findById(id).orElse(null);
        sach.setIsActive(1);
        return sachRepository.save(sach);
    }

    @Override
    public Sach unactive(Long id) {
        Sach sach = sachRepository.findById(id).orElse(null);
        sach.setIsActive(0);
        return sachRepository.save(sach);
    }

    @Override
    public List<Sach> findBanChay(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<Sach> result = sachRepository.findBanChay(pageable);
        loadHinhAnh(result);
        return result;
    }

    @Override
    public List<Sach> findMoiNhat(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<Sach> result = sachRepository.findByIsActiveOrderByMaSachDesc(1, pageable);
        loadHinhAnh(result);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Sach> findLienQuan(int maSach, int limit) {
        Sach sach = sachRepository.findById((long) maSach).orElse(null);
        if (sach == null || sach.getListTheLoai() == null || sach.getListTheLoai().isEmpty()) {
            return new ArrayList<>();
        }
        List<Integer> maTheLoais = sach.getListTheLoai().stream()
                .map(TheLoai::getMaTheLoai)
                .collect(Collectors.toList());
        Pageable pageable = PageRequest.of(0, limit);
        List<Sach> result = sachRepository.findLienQuan(maTheLoais, maSach, pageable);
        loadHinhAnh(result);
        return result;
    }

    @Override
    public Sach findBySlug(String slug) {
        Sach sach = sachRepository.findBySlug(slug);
        if (sach != null) {
            List<HinhAnh> hinhAnhList = hinhAnhRepository.findAll((root, query, builder) ->
                    builder.equal(root.get("sach").get("maSach"), sach.getMaSach())
            );
            sach.setListHinhAnh(hinhAnhList);
        }
        return sach;
    }

    // Auto-generate slug if not set
    private void generateSlugIfMissing(Sach sach) {
        if (sach.getSlug() == null || sach.getSlug().isEmpty()) {
            String slug = SlugUtil.toSlug(sach.getTenSach());
            if (sachRepository.existsBySlug(slug)) {
                slug = slug + "-" + sach.getMaSach();
            }
            sach.setSlug(slug);
        }
    }

    // Load hinhAnh list for a batch of books
    private void loadHinhAnh(List<Sach> sachList) {
        sachList.forEach(sach -> {
            List<HinhAnh> hinhAnhList = hinhAnhRepository.findAll((root, query, builder) ->
                    builder.equal(root.get("sach").get("maSach"), sach.getMaSach())
            );
            sach.setListHinhAnh(hinhAnhList);
        });
    }
}
