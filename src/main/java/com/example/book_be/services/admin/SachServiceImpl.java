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

import java.io.IOException;
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

    @Autowired
    private BookImageStorageService bookImageStorageService;

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

        sachPage.getContent().forEach(this::loadImages);
        return sachPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Sach save(Sach sach) {
        Sach persisted = sachRepository.save(sach);
        generateSlugIfMissing(persisted);
        persisted = sachRepository.save(persisted);
        persistBookImages(persisted, sach.getListImageStr());
        loadImages(persisted);
        return persisted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Sach update(Sach bo) throws Exception {
        Sach existing = sachRepository.findById(Long.valueOf(bo.getMaSach())).orElse(null);
        if (existing == null) {
            throw new Exception("Sach not found");
        }

        mergeUpdatableFields(existing, bo);
        generateSlugIfMissing(existing);
        Sach savedSach = sachRepository.save(existing);
        persistBookImages(savedSach, bo.getListImageStr());
        loadImages(savedSach);
        return savedSach;
    }

    @Override
    public Sach delete(Long id) {
        return null;
    }

    @Override
    public Sach findById(Long id) {
        Sach sach = sachRepository.findById(id).orElse(null);
        if (sach != null) {
            loadImages(sach);
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
        result.forEach(this::loadImages);
        return result;
    }

    @Override
    public List<Sach> findMoiNhat(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<Sach> result = sachRepository.findByIsActiveOrderByMaSachDesc(1, pageable);
        result.forEach(this::loadImages);
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
        result.forEach(this::loadImages);
        return result;
    }

    @Override
    public Sach findBySlug(String slug) {
        Sach sach = sachRepository.findBySlug(slug);
        if (sach != null) {
            loadImages(sach);
        }
        return sach;
    }

    private void mergeUpdatableFields(Sach target, Sach source) {
        target.setTenSach(source.getTenSach());
        target.setTenTacGia(source.getTenTacGia());
        target.setMoTa(source.getMoTa());
        target.setGiaNiemYet(source.getGiaNiemYet());
        target.setGiaBan(source.getGiaBan());
        target.setSoLuong(source.getSoLuong());
        target.setTrungBinhXepHang(source.getTrungBinhXepHang());
        target.setISBN(source.getISBN());

        if (source.getSlug() != null && !source.getSlug().isBlank()) {
            target.setSlug(source.getSlug());
        }
        if (source.getIsActive() != null) {
            target.setIsActive(source.getIsActive());
        }
        if (source.getNhaCungCap() != null) {
            target.setNhaCungCap(source.getNhaCungCap());
        }
        if (source.getListTheLoai() != null) {
            target.setListTheLoai(source.getListTheLoai());
        }
    }

    private void persistBookImages(Sach sach, List<String> imageSources) {
        try {
            bookImageStorageService.syncBookImages(sach, imageSources);
        } catch (IOException e) {
            throw new IllegalStateException("Khong the dong bo anh sach", e);
        }
    }

    private void generateSlugIfMissing(Sach sach) {
        if (sach.getSlug() == null || sach.getSlug().isEmpty()) {
            String slug = SlugUtil.toSlug(sach.getTenSach());
            if (sachRepository.existsBySlug(slug)) {
                slug = slug + "-" + sach.getMaSach();
            }
            sach.setSlug(slug);
        }
    }

    private void loadImages(Sach sach) {
        List<HinhAnh> hinhAnhList = hinhAnhRepository.findAll((root, query, builder) ->
                builder.equal(root.get("sach").get("maSach"), sach.getMaSach())
        );
        sach.setListHinhAnh(hinhAnhList);
    }
}
