package com.example.book_be.nguoidung.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.Optional;

/**
 * `exported = false`: Spring Data REST khong duoc tu mo endpoint cho bang nay. Danh sach
 * identity cua mot nguoi la du lieu rieng tu, va toan bo API doc/ghi phai di qua tang
 * service co kiem tra quyen so huu.
 */
@RepositoryRestResource(exported = false)
public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, Long> {
    Optional<AuthIdentity> findByProviderAndIssuerAndProviderSubject(
            String provider, String issuer, String providerSubject);

    List<AuthIdentity> findByNguoiDungMaNguoiDung(int maNguoiDung);
}
