# Web Bﾃ｡n Sﾃ｡ch - Backend API

H盻・th盻創g backend REST API cho website thﾆｰﾆ｡ng m蘯｡i ﾄ訴盻㌻ t盻ｭ bﾃ｡n sﾃ｡ch tr盻ｱc tuy蘯ｿn.

## Cﾃｴng Ngh盻・

- **Java 17** + **Spring Boot 3.3.4**
- **MySQL 8.0** + JPA/Hibernate
- **Spring Security** + JWT (jjwt 0.11.5)
- **VNPay** (thanh toﾃ｡n tr盻ｱc tuy蘯ｿn)
- **Spring Mail** (Gmail SMTP)
- **Lombok**, **Docker**

## Yﾃｪu C蘯ｧu

- Java 17+
- MySQL 8.0+
- Maven 3.9+

## Cﾃi ﾄ雪ｺｷt

### Cﾃ｡ch 1: Docker (Khuy蘯ｿn ngh盻・

```bash
docker compose up --build -d
```

T盻ｱ ﾄ黛ｻ冢g kh盻殃 t蘯｡o:
- MySQL (port 3306) + seed data
- Backend (port 8080)
- Frontend (port 3000)

### Cﾃ｡ch 2: Ch蘯｡y th盻ｧ cﾃｴng

1. **T蘯｡o database MySQL:**
```sql
CREATE DATABASE web_ban_sach CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. **Import d盻ｯ li盻㎡ m蘯ｫu:**
```bash
mysql -u root web_ban_sach < db/init/web_ban_sach.sql
mysql -u root web_ban_sach < db/init/zz-setup-admin-and-defaults.sql
```

3. **C蘯･u hﾃｬnh** (`src/main/resources/application.properties`):
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/web_ban_sach
spring.datasource.username=root
spring.datasource.password=
```

4. **Ch蘯｡y 盻ｩng d盻･ng:**
```bash
./mvnw spring-boot:run
```

Backend s蘯ｽ ch蘯｡y t蘯｡i `http://localhost:8080`

## Bi蘯ｿn Mﾃｴi Trﾆｰ盻拵g

| Bi蘯ｿn | M蘯ｷc ﾄ黛ｻ杵h | Mﾃｴ t蘯｣ |
|------|---------|-------|
| `DB_URL` | `jdbc:mysql://localhost:3306/web_ban_sach` | JDBC URL |
| `DB_USERNAME` | `root` | DB username |
| `DB_PASSWORD` | (tr盻創g) | DB password |
| `JWT_SECRET` | Base64 key | JWT signing key |
| `MAIL_USERNAME` | Gmail address | SMTP username |
| `MAIL_PASSWORD` | App password | SMTP password |
| `VNPAY_TMN_CODE` | Sandbox code | VNPay merchant code |
| `VNPAY_HASH_SECRET` | Sandbox key | VNPay secret key |

## API Endpoints

### Cﾃｴng khai
| Method | Path | Mﾃｴ t蘯｣ |
|--------|------|-------|
| POST | `/tai-khoan/dang-ky` | ﾄ斉ハg kﾃｽ tﾃi kho蘯｣n |
| POST | `/tai-khoan/dang-nhap` | ﾄ斉ハg nh蘯ｭp (tr蘯｣ JWT) |
| GET | `/tai-khoan/kich-hoat` | Kﾃｭch ho蘯｡t email |
| GET | `/api/sach?page=0` | Danh sﾃ｡ch sﾃ｡ch |
| GET | `/api/sach/search?tensach=x&page=0&size=8` | Tﾃｬm ki蘯ｿm |
| GET | `/api/sach/{id}` | Chi ti蘯ｿt sﾃ｡ch |

### Yﾃｪu c蘯ｧu ﾄ惰ハg nh蘯ｭp
| Method | Path | Mﾃｴ t蘯｣ |
|--------|------|-------|
| POST | `/api/don-hang/them` | ﾄ雪ｺｷt hﾃng |
| GET | `/api/don-hang/findAll?page=0` | ﾄ脆｡n hﾃng c盻ｧa tﾃｴi |
| POST | `/api/danh-gia/them-danh-gia-v1` | Thﾃｪm ﾄ妥｡nh giﾃ｡ |

### Admin
| Method | Path | Mﾃｴ t蘯｣ |
|--------|------|-------|
| POST | `/api/admin/sach/insert` | Thﾃｪm sﾃ｡ch |
| PUT | `/api/admin/sach/update/{id}` | S盻ｭa sﾃ｡ch |
| POST | `/api/admin/user/phan-quyen` | Phﾃ｢n quy盻］ |

## C蘯･u Trﾃｺc D盻ｱ ﾃ］

```
src/main/java/com/example/book_be/
笏懌楳笏 bo/           # Business Objects (DTO)
笏懌楳笏 config/       # C蘯･u hﾃｬnh (REST, VNPay)
笏懌楳笏 controller/   # REST Controllers
笏・  笏披楳笏 admin/    # Admin controllers
笏懌楳笏 dao/          # JPA Repositories
笏懌楳笏 entity/       # JPA Entities (14 b蘯｣ng)
笏懌楳笏 security/     # Spring Security + JWT config
笏披楳笏 services/     # Business logic
    笏懌楳笏 JWT/      # JWT service & filter
    笏懌楳笏 admin/    # Qu蘯｣n lﾃｽ sﾃ｡ch, user
    笏懌楳笏 cart/     # Gi盻・hﾃng, ﾄ柁｡n hﾃng
    笏懌楳笏 email/    # G盻ｭi email
    笏披楳笏 review/   # ﾄ静｡nh giﾃ｡
```

## Tﾃi Li盻㎡

Xem thﾃｪm trong thﾆｰ m盻･c `docs/`:
- [T盻貧g quan d盻ｱ ﾃ｡n](docs/project-overview-pdr.md)
- [Tﾃｳm t蘯ｯt mﾃ｣ ngu盻渡](docs/codebase-summary.md)
- [Tiﾃｪu chu蘯ｩn code](docs/code-standards.md)
- [Ki蘯ｿn trﾃｺc h盻・th盻創g](docs/system-architecture.md)
- [L盻・trﾃｬnh phﾃ｡t tri盻ハ](docs/project-roadmap.md)

## Frontend

Frontend React nam o repo rieng: `../book_FE` (port 3000). Docker Compose se build FE tu duong dan nay.

