# L盻・Trﾃｬnh Phﾃ｡t Tri盻ハ

## Tr蘯｡ng Thﾃ｡i Hi盻㌻ T蘯｡i: MVP Hoﾃn Thi盻㌻

### Giai ﾄ塵蘯｡n 1: Core Backend (Hoﾃn thﾃnh)
- [x] Thi蘯ｿt k蘯ｿ database schema (14 b蘯｣ng)
- [x] Entity JPA v盻嬖 ﾄ黛ｺｧy ﾄ黛ｻｧ quan h盻・
- [x] Spring Data REST auto-expose repositories
- [x] CORS configuration
- [x] Lombok integration

### Giai ﾄ塵蘯｡n 2: Xﾃ｡c Th盻ｱc & B蘯｣o M蘯ｭt (Hoﾃn thﾃnh)
- [x] ﾄ斉ハg kﾃｽ tﾃi kho蘯｣n
- [x] Kﾃｭch ho蘯｡t qua email (Gmail SMTP)
- [x] ﾄ斉ハg nh蘯ｭp JWT (30 phﾃｺt expiry)
- [x] BCrypt password hashing
- [x] Phﾃ｢n quy盻］ ADMIN/STAFF/USER
- [x] Rate limiting ﾄ惰ハg nh蘯ｭp (5 l蘯ｧn/5 phﾃｺt)
- [x] Stateless session

### Giai ﾄ塵蘯｡n 3: Qu蘯｣n Lﾃｽ Sﾃ｡ch (Hoﾃn thﾃnh)
- [x] CRUD sﾃ｡ch
- [x] Tﾃｬm ki蘯ｿm theo tﾃｪn
- [x] L盻皇 theo th盻・lo蘯｡i
- [x] Phﾃ｢n trang
- [x] Qu蘯｣n lﾃｽ hﾃｬnh 蘯｣nh sﾃ｡ch
- [x] Kﾃｭch ho蘯｡t/vﾃｴ hi盻㎡ hﾃｳa sﾃ｡ch

### Giai ﾄ塵蘯｡n 4: Gi盻・Hﾃng & ﾄ脆｡n Hﾃng (Hoﾃn thﾃnh)
- [x] Thﾃｪm/xﾃｳa/c蘯ｭp nh蘯ｭt gi盻・hﾃng
- [x] ﾄ雪ｺｷt hﾃng (cﾃｳ ﾄ惰ハg nh蘯ｭp)
- [x] ﾄ雪ｺｷt hﾃng nhanh (khﾃｴng ﾄ惰ハg nh蘯ｭp)
- [x] Thanh toﾃ｡n VNPay (sandbox)
- [x] Email xﾃ｡c nh蘯ｭn ﾄ柁｡n hﾃng
- [x] C蘯ｭp nh蘯ｭt tr蘯｡ng thﾃ｡i giao hﾃng

### Giai ﾄ塵蘯｡n 5: ﾄ静｡nh Giﾃ｡ & Admin (Hoﾃn thﾃnh)
- [x] ﾄ静｡nh giﾃ｡ sﾃ｡ch
- [x] Admin ki盻ノ duy盻㏄ bﾃｬnh lu蘯ｭn
- [x] Admin qu蘯｣n lﾃｽ ngﾆｰ盻拱 dﾃｹng
- [x] Admin phﾃ｢n quy盻］
- [x] Admin qu蘯｣n lﾃｽ ﾄ柁｡n hﾃng

### Giai ﾄ塵蘯｡n 6: Docker & Deployment (Hoﾃn thﾃnh)
- [x] Dockerfile multi-stage build
- [x] docker-compose (MySQL + Backend + Frontend)
- [x] SQL init scripts cho Docker

## C蘯｣i Ti蘯ｿn Ti盻［ Nﾄハg

### B蘯｣o M蘯ｭt
- [ ] Chuy盻ハ VNPay sang production
- [ ] Refresh token mechanism
- [ ] Rate limiting phﾃ｢n tﾃ｡n (Redis thay ConcurrentHashMap)
- [ ] Input validation/sanitization ch蘯ｷt ch蘯ｽ hﾆ｡n
- [ ] HTTPS enforcement

### Hi盻㎡ Nﾄハg
- [ ] Caching (Redis/Caffeine)
- [ ] Database indexing optimization
- [ ] Connection pooling tuning (HikariCP)
- [ ] Lazy loading optimization

### Tﾃｭnh Nﾄハg
- [x] Upload hinh anh sach qua Cloudinary, ngung luu base64 moi
- [ ] Tﾃｬm ki蘯ｿm nﾃ｢ng cao (Elasticsearch)
- [ ] Thﾃｴng bﾃ｡o real-time (WebSocket)
- [ ] Qu蘯｣n lﾃｽ kho hﾃng
- [ ] Bﾃ｡o cﾃ｡o th盻創g kﾃｪ
- [ ] API documentation (Swagger/OpenAPI)

### Code Quality
- [ ] Unit tests & integration tests
- [ ] Constructor injection thay field injection
- [ ] Global exception handler (`@ControllerAdvice`)
- [ ] DTO pattern nh蘯･t quﾃ｡n (tﾃ｡ch entity kh盻淑 API response)
- [ ] API versioning
- [ ] Logging framework (SLF4J structured logging)

