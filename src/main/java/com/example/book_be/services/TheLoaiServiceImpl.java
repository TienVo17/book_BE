package com.example.book_be.services;

import com.example.book_be.dao.TheLoaiRepository;
import com.example.book_be.dto.theloai.TheLoaiAdminResponse;
import com.example.book_be.dto.theloai.TheLoaiAdminUpsertRequest;
import com.example.book_be.dto.theloai.TheLoaiResponse;
import com.example.book_be.entity.TheLoai;
import com.example.book_be.util.SlugUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class TheLoaiServiceImpl implements TheLoaiService {

    @Autowired
    private TheLoaiRepository theLoaiRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TheLoaiResponse> getDanhSachTheLoaiPublic() {
        return theLoaiRepository.findAllWithBookCount().stream()
                .filter(row -> theLoaiRepository.countActiveSachByMaTheLoai(((Number) row[0]).intValue()) > 0)
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TheLoaiResponse getTheLoaiPublicBySlug(String slug) {
        TheLoai theLoai = findBySlugOrThrow(slug);
        long soLuongSach = theLoaiRepository.countSachByMaTheLoai(theLoai.getMaTheLoai());
        long soLuongSachActive = theLoaiRepository.countActiveSachByMaTheLoai(theLoai.getMaTheLoai());
        if (soLuongSachActive <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thể loại");
        }
        return new TheLoaiResponse(theLoai.getMaTheLoai(), theLoai.getTenTheLoai(), theLoai.getSlug(), soLuongSach);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TheLoaiAdminResponse> getDanhSachTheLoaiAdmin() {
        return theLoaiRepository.findAllWithBookCount().stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Override
    @Transactional
    public TheLoaiAdminResponse taoTheLoai(TheLoaiAdminUpsertRequest request) {
        String tenTheLoai = normalizeTenTheLoai(request.getTenTheLoai());
        String slug = taoSlug(tenTheLoai);
        validateUnique(tenTheLoai, slug, null);

        TheLoai theLoai = new TheLoai();
        theLoai.setTenTheLoai(tenTheLoai);
        theLoai.setSlug(slug);
        TheLoai saved = theLoaiRepository.save(theLoai);
        return new TheLoaiAdminResponse(saved.getMaTheLoai(), saved.getTenTheLoai(), saved.getSlug(), 0L, true);
    }

    @Override
    @Transactional
    public TheLoaiAdminResponse capNhatTheLoai(Integer maTheLoai, TheLoaiAdminUpsertRequest request) {
        TheLoai existing = theLoaiRepository.findById(maTheLoai)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thể loại"));

        String tenTheLoai = normalizeTenTheLoai(request.getTenTheLoai());
        String slug = taoSlug(tenTheLoai);
        validateUnique(tenTheLoai, slug, maTheLoai);

        existing.setTenTheLoai(tenTheLoai);
        existing.setSlug(slug);
        TheLoai saved = theLoaiRepository.save(existing);
        long soLuongSach = theLoaiRepository.countSachByMaTheLoai(saved.getMaTheLoai());
        return new TheLoaiAdminResponse(saved.getMaTheLoai(), saved.getTenTheLoai(), saved.getSlug(), soLuongSach, soLuongSach == 0);
    }

    @Override
    @Transactional
    public void xoaTheLoai(Integer maTheLoai) {
        TheLoai existing = theLoaiRepository.findById(maTheLoai)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thể loại"));
        long soLuongSach = theLoaiRepository.countSachByMaTheLoai(maTheLoai);
        if (soLuongSach > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể xóa thể loại đang gắn với sách");
        }
        theLoaiRepository.delete(existing);
    }

    private TheLoai findBySlugOrThrow(String slug) {
        String normalizedSlug = slug == null ? null : slug.trim();
        Optional<TheLoai> result = normalizedSlug == null || normalizedSlug.isBlank()
                ? Optional.empty()
                : theLoaiRepository.findBySlug(normalizedSlug);
        return result.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thể loại"));
    }

    private String normalizeTenTheLoai(String tenTheLoai) {
        if (tenTheLoai == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên thể loại không được để trống");
        }
        String normalized = tenTheLoai.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên thể loại không được để trống");
        }
        return normalized;
    }

    private String taoSlug(String tenTheLoai) {
        String slug = SlugUtil.toSlug(tenTheLoai);
        if (slug == null || slug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể tạo slug hợp lệ cho thể loại");
        }
        return slug;
    }

    private void validateUnique(String tenTheLoai, String slug, Integer currentId) {
        boolean duplicatedName = currentId == null
                ? theLoaiRepository.existsByTenTheLoaiIgnoreCase(tenTheLoai)
                : theLoaiRepository.existsByTenTheLoaiIgnoreCaseAndMaTheLoaiNot(tenTheLoai, currentId);
        if (duplicatedName) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên thể loại đã tồn tại");
        }

        boolean duplicatedSlug = currentId == null
                ? theLoaiRepository.existsBySlug(slug)
                : theLoaiRepository.existsBySlugAndMaTheLoaiNot(slug, currentId);
        if (duplicatedSlug) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug thể loại đã tồn tại");
        }
    }

    private TheLoaiResponse toResponse(Object[] row) {
        return new TheLoaiResponse(
                ((Number) row[0]).intValue(),
                (String) row[1],
                (String) row[2],
                ((Number) row[3]).longValue()
        );
    }

    private TheLoaiAdminResponse toAdminResponse(Object[] row) {
        long soLuongSach = ((Number) row[3]).longValue();
        return new TheLoaiAdminResponse(
                ((Number) row[0]).intValue(),
                (String) row[1],
                (String) row[2],
                soLuongSach,
                soLuongSach == 0
        );
    }
}
