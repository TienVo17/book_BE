# Code Reviewer Agent Memory - book_BE-main

## Project Structure
- Spring Boot 3.3.4 + Java 17, package: `com.example.book_be`
- Entities use `int` IDs but repos use `JpaRepository<Entity, Long>` (autoboxing)
- Vietnamese naming: entities (Sach, NguoiDung, DonHang), controllers, services
- `ddl-auto=update` strategy, MySQL database
- Security: JWT-based auth, BCrypt passwords, role-based (ADMIN/USER)
- CORS: global config in SecurityConfiguration + per-controller @CrossOrigin (inconsistent)

## Key Patterns
- Endpoint security: `Endpoints.java` arrays -> `SecurityConfiguration.java` matchers
- Lazy loading: all @OneToMany are LAZY, images loaded separately via `loadHinhAnh()`
- Entity serialization: `@JsonIgnore` on relationship fields to prevent recursion
- Service layer: interface + impl pattern (e.g., `CouponService` / `CouponServiceImpl`)

## Recurring Issues Found
- `e.printStackTrace()` used instead of SLF4J logging (15+ occurrences)
- Missing `@JsonIgnore` causes serialization issues (NguoiDung.danhSachSachYeuThich)
- LazyInitializationException risk when accessing LAZY collections outside transactions
- `toString()` on entities references LAZY collections and sensitive fields
- Authority checks using `contains("ADMIN")` on GrantedAuthority collection won't work
- ID type mismatch: entity `int` vs repository `Long` requires casting

## Security Watch Points
- Password hash in NguoiDung.toString()
- Public endpoints that should be authenticated (them-don-hang-moi)
- AnonymousUser check pattern: must check `"anonymousUser".equals(auth.getName())`
- Rate limiting is in-memory only (ConcurrentHashMap) - no cleanup, no persistence
- Hardcoded sender email: tienvovan917@gmail.com in multiple files

## Frontend (book_fe-master) Patterns
- React + TypeScript, react-router-dom v6, react-toastify for notifications
- API layer: `src/api/*.ts` with `authRequest()` wrapper for JWT-authenticated calls
- `authRequest()` returns parsed JSON (not Response), callers must NOT chain `.json()`
- All API files hardcode `http://localhost:8080` -- no env var centralization
- JWT stored in `localStorage('jwt')`, decoded via `atob(jwt.split('.')[1])`
- Cart stored in `localStorage('gioHang')`, synced via custom `cartUpdated` event
- Admin routes nested under `/quan-ly/*` -- child routes must use relative paths (no leading `/`)

## Frontend Recurring Issues
- `authRequest` return value treated as raw `Response` (double-parse bug in HoSoNguoiDung)
- JWT decode without try-catch crashes app on malformed tokens (Navbar, AdminSidebar)
- `dangerouslySetInnerHTML` used on `moTa` without DOMPurify sanitization
- Console.log of JWT tokens in SachApi.ts (security leak)
- Wishlist heart state not synced in SachProps card components

## File Locations
- Security config: `src/main/java/.../security/Endpoints.java`, `SecurityConfiguration.java`
- App config: `src/main/resources/application.properties`
- Frontend API layer: `book_fe-master/src/api/`
- Frontend routes: `book_fe-master/src/App.tsx`
- Reports output: `plans/reports/`
