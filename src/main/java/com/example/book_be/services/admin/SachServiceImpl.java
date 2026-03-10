package com.example.book_be.services.admin;

import com.example.book_be.bo.SachAdminUpsertBo;
import com.example.book_be.bo.SachBo;
import com.example.book_be.bo.SachThongTinChiTietBo;
import com.example.book_be.dao.HinhAnhRepository;
import com.example.book_be.dao.SachRepository;
import com.example.book_be.dao.TheLoaiRepository;
import com.example.book_be.entity.HinhAnh;
import com.example.book_be.entity.Sach;
import com.example.book_be.entity.SachThongTinChiTiet;
import com.example.book_be.entity.TheLoai;
import com.example.book_be.util.BookDescriptionSanitizer;
import com.example.book_be.util.SlugUtil;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SachServiceImpl implements SachService {

    @Autowired
    private SachRepository sachRepository;

    @Autowired
    private HinhAnhRepository hinhAnhRepository;

    @Autowired
    private BookImageStorageService bookImageStorageService;

    @Autowired
    private TheLoaiRepository theLoaiRepository;

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
                query.distinct(true);
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
        return save(toAdminBo(sach));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Sach save(SachAdminUpsertBo bo) {
        validateBookRequest(bo, null);
        Sach sach = new Sach();
        applyAdminBo(sach, bo);
        generateSlug(sach, null);
        Sach persisted = sachRepository.save(sach);
        persistBookImages(persisted, bo.getListImageStr());
        loadImages(persisted);
        return persisted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Sach update(Sach bo) throws Exception {
        return update(toAdminBo(bo));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Sach update(SachAdminUpsertBo bo) throws Exception {
        if (bo.getMaSach() == null) {
            throw new IllegalArgumentException("Mã sách là bắt buộc khi cập nhật");
        }

        Sach existing = sachRepository.findById(Long.valueOf(bo.getMaSach())).orElse(null);
        if (existing == null) {
            throw new Exception("Sach not found");
        }

        validateBookRequest(bo, existing.getMaSach());
        applyAdminBo(existing, bo);
        generateSlug(existing, existing.getMaSach());
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
        if (sach == null) {
            throw new IllegalArgumentException("Không tìm thấy sách với id: " + id);
        }
        sach.setIsActive(1);
        return sachRepository.save(sach);
    }

    @Override
    public Sach unactive(Long id) {
        Sach sach = sachRepository.findById(id).orElse(null);
        if (sach == null) {
            throw new IllegalArgumentException("Khong tim thay sach voi id: " + id);
        }
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

    private SachAdminUpsertBo toAdminBo(Sach sach) {
        SachAdminUpsertBo bo = new SachAdminUpsertBo();
        bo.setMaSach(sach.getMaSach() > 0 ? sach.getMaSach() : null);
        bo.setTenSach(sach.getTenSach());
        bo.setTenTacGia(sach.getTenTacGia());
        bo.setIsbn(sach.getISBN());
        bo.setSlug(sach.getSlug());
        bo.setMoTaNgan(sach.getMoTaNgan());
        bo.setMoTaChiTiet(firstNonBlank(sach.getMoTaChiTiet(), sach.getMoTa()));
        bo.setGiaNiemYet(sach.getGiaNiemYet());
        bo.setGiaBan(sach.getGiaBan());
        bo.setSoLuongTon(sach.getSoLuong());
        bo.setIsActive(sach.getIsActive());
        bo.setNhaCungCap(sach.getNhaCungCap());
        bo.setListTheLoai(sach.getListTheLoai());
        bo.setMaTheLoaiList(sach.getListTheLoai() == null ? null : sach.getListTheLoai().stream()
                .map(TheLoai::getMaTheLoai)
                .toList());
        bo.setListImageStr(sach.getListImageStr());

        if (sach.getThongTinChiTiet() != null) {
            SachThongTinChiTietBo chiTietBo = new SachThongTinChiTietBo();
            chiTietBo.setCongTyPhatHanh(sach.getThongTinChiTiet().getCongTyPhatHanh());
            chiTietBo.setNhaXuatBan(sach.getThongTinChiTiet().getNhaXuatBan());
            chiTietBo.setNgayXuatBan(sach.getThongTinChiTiet().getNgayXuatBan() == null ? null : sach.getThongTinChiTiet().getNgayXuatBan().toString());
            chiTietBo.setSoTrang(sach.getThongTinChiTiet().getSoTrang());
            chiTietBo.setLoaiBia(sach.getThongTinChiTiet().getLoaiBia());
            chiTietBo.setNgonNgu(sach.getThongTinChiTiet().getNgonNgu());
            chiTietBo.setKichThuoc(sach.getThongTinChiTiet().getKichThuoc());
            chiTietBo.setTrongLuongGram(sach.getThongTinChiTiet().getTrongLuongGram());
            chiTietBo.setPhienBan(sach.getThongTinChiTiet().getPhienBan());
            bo.setChiTiet(chiTietBo);
        }
        return bo;
    }

    private void applyAdminBo(Sach target, SachAdminUpsertBo source) {
        target.setTenSach(trimToNull(source.getTenSach()));
        target.setTenTacGia(trimToNull(source.getTenTacGia()));
        target.setISBN(trimToNull(source.getIsbn()));
        target.setGiaNiemYet(source.getGiaNiemYet() == null ? 0 : source.getGiaNiemYet());
        target.setGiaBan(source.getGiaBan() == null ? 0 : source.getGiaBan());
        target.setSoLuong(source.getSoLuongTon() == null ? 0 : source.getSoLuongTon());
        target.setIsActive(source.getIsActive() == null ? 1 : source.getIsActive());
        target.setNhaCungCap(source.getNhaCungCap());
        target.setListTheLoai(resolveTheLoaiList(source));
        target.setSlug(trimToNull(source.getSlug()));

        String sanitizedRichText = BookDescriptionSanitizer.sanitizeRichText(source.getMoTaChiTiet());
        target.setMoTaChiTiet(sanitizedRichText);
        target.setMoTa(trimToNull(firstNonBlank(sanitizedRichText, target.getMoTa())));
        if (source.getMoTaNgan() != null && !source.getMoTaNgan().isBlank()) {
            target.setMoTaNgan(source.getMoTaNgan().trim());
        } else {
            target.setMoTaNgan(BookDescriptionSanitizer.summarizePlainText(sanitizedRichText, 280));
        }

        updateBookDetail(target, source.getChiTiet());
    }

    private void updateBookDetail(Sach sach, SachThongTinChiTietBo bo) {
        if (bo == null) {
            sach.setThongTinChiTiet(null);
            return;
        }

        SachThongTinChiTiet chiTiet = sach.getThongTinChiTiet();
        if (chiTiet == null) {
            chiTiet = new SachThongTinChiTiet();
            chiTiet.setSach(sach);
        }
        chiTiet.setCongTyPhatHanh(trimToNull(bo.getCongTyPhatHanh()));
        chiTiet.setNhaXuatBan(trimToNull(bo.getNhaXuatBan()));
        chiTiet.setNgayXuatBan(parseNgayXuatBan(bo.getNgayXuatBan()));
        chiTiet.setSoTrang(bo.getSoTrang());
        chiTiet.setLoaiBia(trimToNull(bo.getLoaiBia()));
        chiTiet.setNgonNgu(trimToNull(bo.getNgonNgu()));
        chiTiet.setKichThuoc(trimToNull(bo.getKichThuoc()));
        chiTiet.setTrongLuongGram(bo.getTrongLuongGram());
        chiTiet.setPhienBan(trimToNull(bo.getPhienBan()));
        sach.setThongTinChiTiet(chiTiet);
    }

    private LocalDate parseNgayXuatBan(String ngayXuatBan) {
        if (ngayXuatBan == null || ngayXuatBan.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(ngayXuatBan.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Ngày xuất bản phải theo định dạng yyyy-MM-dd");
        }
    }

    private void validateBookRequest(SachAdminUpsertBo bo, Integer currentBookId) {
        if (bo.getTenSach() == null || bo.getTenSach().isBlank()) {
            throw new IllegalArgumentException("Tên sách không được để trống");
        }
        if (bo.getGiaBan() == null || bo.getGiaBan() < 0) {
            throw new IllegalArgumentException("Giá bán phải lớn hơn hoặc bằng 0");
        }
        if (bo.getGiaNiemYet() == null || bo.getGiaNiemYet() < 0) {
            throw new IllegalArgumentException("Giá niêm yết phải lớn hơn hoặc bằng 0");
        }
        if (bo.getGiaNiemYet() < bo.getGiaBan()) {
            throw new IllegalArgumentException("Giá niêm yết phải lớn hơn hoặc bằng giá bán");
        }
        if (bo.getSoLuongTon() == null || bo.getSoLuongTon() < 0) {
            throw new IllegalArgumentException("Số lượng tồn phải lớn hơn hoặc bằng 0");
        }

        String isbn = trimToNull(bo.getIsbn());
        bo.setIsbn(isbn);
        if (isbn != null && !isbn.matches("[0-9Xx-]{10,17}")) {
            throw new IllegalArgumentException("ISBN không hợp lệ (chỉ chấp nhận 10-17 ký tự gồm số, X, x, dấu gạch)");
        }

        String slug = trimToNull(bo.getSlug());
        if (slug != null) {
            Sach existingBySlug = sachRepository.findBySlug(slug);
            if (existingBySlug != null && !Objects.equals(existingBySlug.getMaSach(), currentBookId)) {
                throw new IllegalArgumentException("Slug đã tồn tại");
            }
        }
    }

    private List<TheLoai> resolveTheLoaiList(SachAdminUpsertBo source) {
        if (source.getMaTheLoaiList() != null) {
            List<TheLoai> theLoaiList = theLoaiRepository.findAllById(source.getMaTheLoaiList());
            if (theLoaiList.size() != source.getMaTheLoaiList().size()) {
                throw new IllegalArgumentException("Một hoặc nhiều thể loại không tồn tại");
            }
            return theLoaiList;
        }
        return source.getListTheLoai();
    }

    private void persistBookImages(Sach sach, List<String> imageSources) {
        try {
            bookImageStorageService.syncBookImages(sach, imageSources);
        } catch (IOException e) {
            throw new IllegalStateException("Không thể đồng bộ ảnh sách", e);
        }
    }

    private void generateSlug(Sach sach, Integer currentBookId) {
        String desiredSlug = trimToNull(sach.getSlug());
        String slug = desiredSlug != null ? SlugUtil.toSlug(desiredSlug) : SlugUtil.toSlug(sach.getTenSach());
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Không thể tạo slug hợp lệ cho sách");
        }

        Sach existingBySlug = sachRepository.findBySlug(slug);
        if (existingBySlug != null && !Objects.equals(existingBySlug.getMaSach(), currentBookId)) {
            if (currentBookId == null) {
                slug = slug + "-" + System.currentTimeMillis();
            } else {
                slug = slug + "-" + currentBookId;
            }
        }
        sach.setSlug(slug);
    }

    private void loadImages(Sach sach) {
        List<HinhAnh> hinhAnhList = hinhAnhRepository.findAll((root, query, builder) ->
                builder.equal(root.get("sach").get("maSach"), sach.getMaSach())
        );
        sach.setListHinhAnh(hinhAnhList);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return trimToNull(fallback);
    }
}
