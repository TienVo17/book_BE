# Tóm Tắt Mã Nguồn

## Thống Kê

- **Tổng LOC**: ~3815 dòng (Java + SQL + config)
- **Số file Java**: 68
- **Java version**: 17
- **Package gốc**: `com.example.book_be`

## Cấu Trúc Package

```
src/main/java/com/example/book_be/
├── BookBeApplication.java          # Entry point
├── bo/                             # Business Objects (DTO)
│   ├── BaseBo.java                 # Base DTO với page/pageSize
│   ├── SachBo.java                 # DTO cho tìm kiếm sách
│   ├── UserBo.java                 # DTO cho admin quản lý user
│   ├── DanhGiaBo.java              # DTO cho đánh giá
│   └── PhanQuyenBo.java            # DTO cho phân quyền
├── config/
│   ├── RestConfig.java             # Spring Data REST config, CORS, expose entity IDs
│   └── VnPayConfig.java            # VNPay crypto utils (MD5, SHA256, HMAC-SHA512)
├── controller/
│   ├── TaiKhoanController.java     # Đăng ký, đăng nhập, kích hoạt tài khoản
│   ├── SachUserController.java     # API sách cho user (CRUD, search, images)
│   ├── DonHangController.java      # Đơn hàng, thanh toán VNPay, email
│   ├── GioHangController.java      # Thao tác giỏ hàng
│   ├── DanhGiaController.java      # Đánh giá sách
│   └── admin/
│       ├── SachController.java     # Admin quản lý sách
│       ├── UserController.java     # Admin quản lý user
│       ├── BinhLuanController.java # Admin kiểm duyệt bình luận
│       ├── DonHangAdminController.java # Admin quản lý đơn hàng
│       └── QuyenController.java    # Admin quản lý quyền
├── dao/                            # Spring Data JPA Repositories
│   ├── SachRepository.java
│   ├── NguoiDungRepository.java
│   ├── DonHangRepository.java
│   ├── ChiTietDonHangRepository.java
│   ├── GioHangRepository.java
│   ├── HinhAnhRepository.java
│   ├── HinhThucGiaoHangRepository.java
│   ├── HinhThucThanhToanRepository.java
│   ├── NhaCungCapRepository.java
│   ├── QuyenRepository.java
│   ├── SachYeuThichRepository.java
│   ├── SuDanhGiaRepository.java
│   └── TheLoaiRepository.java
├── entity/                         # JPA Entities (14 bảng)
│   ├── Sach.java                   # Sách (maSach, tenSach, tenTacGia, giaBan...)
│   ├── NguoiDung.java              # Người dùng (tenDangNhap, matKhau, email...)
│   ├── DonHang.java                # Đơn hàng (tongTien, trangThaiThanhToan...)
│   ├── ChiTietDonHang.java         # Chi tiết đơn hàng
│   ├── GioHang.java                # Giỏ hàng
│   ├── HinhAnh.java                # Hình ảnh sách
│   ├── TheLoai.java                # Thể loại sách
│   ├── Quyen.java                  # Quyền (ADMIN, STAFF, USER)
│   ├── SuDanhGia.java              # Đánh giá sách
│   ├── SachYeuThich.java           # Sách yêu thích
│   ├── NhaCungCap.java             # Nhà cung cấp
│   ├── HinhThucThanhToan.java      # Hình thức thanh toán
│   ├── HinhThucGiaoHang.java       # Hình thức giao hàng
│   └── ThongBao.java               # Thông báo
├── security/
│   ├── SecurityConfiguration.java  # Filter chain, JWT, CORS, BCrypt
│   ├── Endpoints.java              # Danh sách endpoint public/admin
│   ├── LoginRequest.java           # DTO đăng nhập
│   └── JwtResponse.java            # DTO response JWT
└── services/
    ├── JWT/
    │   ├── JwtService.java         # Tạo/xác thực JWT token
    │   └── Jwtfilter.java          # Filter xác thực request
    ├── TaiKhoanService.java        # Đăng ký, kích hoạt tài khoản
    ├── UserService.java            # UserDetailsService (Spring Security)
    ├── UserServiceImpl.java
    ├── VNPayService.java           # Tạo đơn VNPay, xác thực kết quả
    ├── admin/
    │   ├── SachService.java        # Interface quản lý sách
    │   ├── SachServiceImpl.java    # Impl: CRUD, search, phân trang
    │   ├── AdminUserService.java   # Interface quản lý user (admin)
    │   └── AdminUserServiceImpl.java
    ├── cart/
    │   ├── CartService.java        # Interface giỏ hàng
    │   ├── CartServiceImpl.java
    │   ├── OrderService.java       # Interface đơn hàng
    │   └── OrderServiceImpl.java
    ├── email/
    │   ├── EmailService.java       # Interface gửi email
    │   └── EmailServiceImpl.java
    └── review/
        ├── DanhGiaService.java     # Interface đánh giá
        └── DanhGiaServiceImpl.java
```

## Quan Hệ Thực Thể (Entity Relationships)

```
                    ┌─────────────┐
                    │  TheLoai    │
                    │ (Thể loại) │
                    └──────┬──────┘
                           │ M:N (sach_theloai)
                           │
┌──────────┐        ┌──────┴──────┐        ┌──────────────┐
│NhaCungCap├───N:1──┤    Sach     ├──1:N──►│   HinhAnh    │
│(Nhà CC)  │        │   (Sách)   │        │ (Hình ảnh)   │
└──────────┘        └──┬──┬──┬───┘        └──────────────┘
                       │  │  │
              1:N──────┘  │  └──────1:N
              │           │           │
     ┌────────┴───┐  ┌────┴────┐  ┌──┴──────────┐
     │ SuDanhGia  │  │GioHang  │  │SachYeuThich │
     │ (Đánh giá) │  │(Giỏ)   │  │(Yêu thích)  │
     └─────┬──────┘  └────┬───┘  └──────┬──────┘
           │ N:1          │ N:1         │ N:1
           │              │             │
           └──────────────┼─────────────┘
                          │
                   ┌──────┴──────┐
                   │  NguoiDung  │
                   │(Người dùng) │
                   └──────┬──────┘
                          │ M:N (nguoidung_quyen)
                   ┌──────┴──────┐
                   │   Quyen     │
                   │  (Quyền)    │
                   └─────────────┘

NguoiDung ──1:N──► DonHang ──1:N──► ChiTietDonHang ──N:1──► Sach
                      │
                      ├──N:1──► HinhThucThanhToan
                      └──N:1──► HinhThucGiaoHang
```

## File Cấu Hình

| File | Mô tả |
|------|-------|
| `pom.xml` | Maven dependencies (Spring Boot, JPA, Security, JWT, VNPay, Lombok) |
| `application.properties` | DB URL, JWT secret, SMTP config (tất cả hỗ trợ env vars) |
| `Dockerfile` | Multi-stage build: Maven → JRE 17 |
| `docker-compose.yml` | 3 services: MySQL, Backend, Frontend |
| `db/init/*.sql` | Scripts khởi tạo DB & dữ liệu mẫu |
| `.gitignore` | Loại trừ target, IDE files |

## File Lớn Nhất (theo LOC)

1. `db/init/web_ban_sach.sql` - 732 dòng (schema + seed data)
2. `controller/DonHangController.java` - 219 dòng
3. `services/admin/SachServiceImpl.java` - 138 dòng
4. `config/VnPayConfig.java` - 122 dòng
5. `services/VNPayService.java` - 118 dòng
