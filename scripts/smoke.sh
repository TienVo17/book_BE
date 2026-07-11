#!/usr/bin/env bash
# Characterization smoke test — khoa hanh vi hien tai cua cac endpoint trong yeu.
# Muc tieu: bat regression khi refactor package (RT-2: chi assert field on dinh, khong so full-JSON).
# Dung: ./scripts/smoke.sh  [BASE_URL]   (mac dinh http://localhost:8080)
set -u
BASE="${1:-http://localhost:8080}"
# Credential admin cho fixture smoke — DB phai co admin voi mat khau nay (xem scripts/smoke-setup.sh)
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-Smoke@12345}"
PASS=0; FAIL=0

req() { # method path [data] [auth]  -> echoes "HTTP_CODE\n BODY" via globals CODE/BODY
  local method="$1" path="$2" data="${3:-}" auth="${4:-}"
  local args=(-s -o /tmp/smoke_body -w '%{http_code}' -X "$method" "$BASE$path")
  [ -n "$data" ] && args+=(-H 'Content-Type: application/json' -d "$data")
  [ -n "$auth" ] && args+=(-H "Authorization: Bearer $auth")
  CODE="$(curl "${args[@]}")"; BODY="$(cat /tmp/smoke_body)"
}
ok()  { PASS=$((PASS+1)); echo "  [PASS] $1"; }
bad() { FAIL=$((FAIL+1)); echo "  [FAIL] $1"; }

echo "== Smoke @ $BASE =="

# 0. Readiness (retry ~60s)
for i in $(seq 1 30); do
  req GET "/api/sach?page=0"; [ "$CODE" = "200" ] && break; sleep 2
done
[ "$CODE" = "200" ] && ok "backend reachable (GET /api/sach = 200)" || { bad "backend khong len (GET /api/sach = $CODE)"; echo "PASS=$PASS FAIL=$FAIL"; exit 1; }

# 1. Catalog
req GET "/api/sach?page=0"
echo "$BODY" | grep -q '"maSach"' && ok "catalog co sach (field maSach)" || bad "catalog khong co sach"
req GET "/api/the-loai"
[ "$CODE" = "200" ] && ok "GET /api/the-loai = 200" || bad "GET /api/the-loai = $CODE"
req GET "/api/sach/slug/dac-nhan-tam"
[ "$CODE" = "200" ] && ok "GET /api/sach/slug/{slug} = 200" || bad "slug detail = $CODE"

# 2. Auth: login admin -> JWT
req POST "/tai-khoan/dang-nhap" "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}"
JWT="$(echo "$BODY" | grep -oE 'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' | head -1)"
{ [ "$CODE" = "200" ] && [ -n "$JWT" ]; } && ok "login admin -> JWT" || bad "login admin ($CODE, jwt=${JWT:+yes})"

# 3. Security: /api/admin/user an danh -> 401/403
req GET "/api/admin/user?page=0"
{ [ "$CODE" = "401" ] || [ "$CODE" = "403" ]; } && ok "admin/user an danh bi chan ($CODE)" || bad "admin/user an danh KHONG bi chan ($CODE)"

# 4. Security: co token admin -> 200 va KHONG lo matKhau/hash
if [ -n "$JWT" ]; then
  req GET "/api/admin/user?page=0" "" "$JWT"
  [ "$CODE" = "200" ] && ok "admin/user voi token admin = 200" || bad "admin/user voi token = $CODE"
  echo "$BODY" | grep -q '"matKhau"' && bad "RO RI: response chua field matKhau" || ok "khong lo field matKhau"
  echo "$BODY" | grep -q '\$2a\$' && bad "RO RI: response chua bcrypt hash" || ok "khong lo bcrypt hash"
else
  bad "bo qua check admin/user (khong co JWT)"
fi

# 5. Cac feature nho (danhgia, giamgia, seo, thongke) — route song + phan quyen dung
req GET "/sitemap.xml"
[ "$CODE" = "200" ] && ok "GET /sitemap.xml = 200 (seo)" || bad "sitemap = $CODE"
req GET "/api/danh-gia/findAll?maSach=1"
[ "$CODE" = "200" ] && ok "GET /api/danh-gia/findAll = 200" || bad "danh-gia findAll = $CODE"
echo "$BODY" | grep -qE '"matKhau"|\$2a\$' && bad "RO RI: danh-gia lo matKhau/hash" || ok "danh-gia khong lo matKhau/hash"
req GET "/api/admin/thong-ke"
{ [ "$CODE" = "401" ] || [ "$CODE" = "403" ]; } && ok "admin/thong-ke an danh bi chan ($CODE)" || bad "admin/thong-ke an danh KHONG bi chan ($CODE)"
if [ -n "$JWT" ]; then
  req GET "/api/admin/thong-ke" "" "$JWT"
  [ "$CODE" = "200" ] && ok "admin/thong-ke voi token = 200" || bad "admin/thong-ke voi token = $CODE"
  req POST "/api/coupon/kiem-tra" '{"ma":"SMOKE_KHONG_TON_TAI","tongTien":100000}' "$JWT"
  { [ "$CODE" = "200" ] && echo "$BODY" | grep -q '"hopLe"'; } && ok "coupon/kiem-tra tra ve hopLe" || bad "coupon/kiem-tra ($CODE)"
else
  bad "bo qua check thong-ke/coupon (khong co JWT)"
fi

# 6. Ton kho + may trang thai don + phan quyen don hang (Cum A) — tu setup + tu don
if [ -n "$JWT" ]; then
  DB_CONT="${DB_CONT:-web_ban_sach_db}"
  dbsql() { docker exec "$DB_CONT" mysql -uroot web_ban_sach -N -e "$1" 2>/dev/null; }
  BOOK_SM=1
  BASELINE_SM="$(dbsql "SELECT COALESCE(MAX(ma_don_hang),0) FROM don_hang;")"
  OLD_STOCK_SM="$(dbsql "SELECT so_luong FROM sach WHERE ma_sach=$BOOK_SM;")"
  ADDR_SM="$(curl -s -X POST "$BASE/api/dia-chi" -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
    -d '{"hoTen":"Smoke A","soDienThoai":"0900000000","diaChiDayDu":"Smoke Addr","macDinh":false}' \
    | grep -oE '"maDiaChi":[0-9]+' | grep -oE '[0-9]+' | head -1)"
  CO_BODY="{\"items\":[{\"maSach\":$BOOK_SM,\"soLuong\":1}],\"maDiaChiGiaoHang\":$ADDR_SM,\"phuongThucThanhToan\":\"COD\"}"

  if [ -n "$ADDR_SM" ]; then
    dbsql "UPDATE sach SET so_luong=10 WHERE ma_sach=$BOOK_SM;"
    # checkout -> tru kho
    req POST "/api/don-hang/them" "$CO_BODY" "$JWT"
    O_A="$(echo "$BODY" | grep -oE '"maDonHang":[0-9]+' | grep -oE '[0-9]+' | head -1)"
    [ "$(dbsql "SELECT so_luong FROM sach WHERE ma_sach=$BOOK_SM;")" = "9" ] && ok "checkout tru kho 10->9" || bad "checkout khong tru kho"
    # huy -> 200 + hoi kho
    req POST "/api/don-hang/huy/$O_A" "" "$JWT"
    { [ "$CODE" = "200" ] && [ "$(dbsql "SELECT so_luong FROM sach WHERE ma_sach=$BOOK_SM;")" = "10" ]; } \
      && ok "huy don -> 200 + hoi kho ->10" || bad "huy don ($CODE) hoac khong hoi kho"
    # huy lan 2 -> 409
    req POST "/api/don-hang/huy/$O_A" "" "$JWT"
    [ "$CODE" = "409" ] && ok "huy lan 2 -> 409" || bad "huy lan 2 -> $CODE"
    # checkout B roi admin advance 2 lan -> 200,200,409
    req POST "/api/don-hang/them" "$CO_BODY" "$JWT"
    O_B="$(echo "$BODY" | grep -oE '"maDonHang":[0-9]+' | grep -oE '[0-9]+' | head -1)"
    req POST "/api/don-hang/cap-nhat-trang-thai-giao-hang/$O_B" "" "$JWT"; A1="$CODE"
    req POST "/api/don-hang/cap-nhat-trang-thai-giao-hang/$O_B" "" "$JWT"; A2="$CODE"
    req POST "/api/don-hang/cap-nhat-trang-thai-giao-hang/$O_B" "" "$JWT"; A3="$CODE"
    { [ "$A1" = "200" ] && [ "$A2" = "200" ] && [ "$A3" = "409" ]; } && ok "admin advance COD 200/200/409" || bad "advance $A1/$A2/$A3"
    # COD giao xong (giao=2) -> tu dong da thanh toan
    PAID_B="$(dbsql "SELECT trang_thai_thanh_toan FROM don_hang WHERE ma_don_hang=$O_B;")"
    [ "$PAID_B" = "1" ] && ok "COD giao xong -> tu dong da thanh toan (thu tien mat)" || bad "COD giao xong thanh_toan=$PAID_B (mong 1)"
    # VNPAY chua thanh toan -> chan cap-nhat giao hang
    dbsql "INSERT INTO don_hang (ngay_tao, tong_tien_san_pham, chi_phi_giao_hang, chi_phi_thanh_toan, tong_tien, ho_ten, so_dien_thoai, trang_thai_thanh_toan, trang_thai_giao_hang, ma_nguoi_dung, ma_hinh_thuc_thanh_toan, version) VALUES (NOW(),0,0,0,0,'VNPAY Chua Tra','0900000000',0,0,1,2,0);"
    O_VNPAY="$(dbsql "SELECT MAX(ma_don_hang) FROM don_hang;")"
    req POST "/api/don-hang/cap-nhat-trang-thai-giao-hang/$O_VNPAY" "" "$JWT"
    [ "$CODE" = "409" ] && ok "VNPAY chua thanh toan -> chan advance (409)" || bad "VNPAY chua tra advance -> $CODE (mong 409)"
    # cap-nhat an danh -> bi chan
    req POST "/api/don-hang/cap-nhat-trang-thai-giao-hang/$O_B" "" ""
    { [ "$CODE" = "401" ] || [ "$CODE" = "403" ]; } && ok "cap-nhat an danh bi chan ($CODE)" || bad "cap-nhat an danh KHONG bi chan ($CODE)"
    # checkout khi het hang -> 400
    dbsql "UPDATE sach SET so_luong=0 WHERE ma_sach=$BOOK_SM;"
    req POST "/api/don-hang/them" "$CO_BODY" "$JWT"
    [ "$CODE" = "400" ] && ok "checkout het hang -> 400" || bad "checkout het hang -> $CODE"
    # admin findAll thay don user khac
    req GET "/api/don-hang/findAll?page=0" "" "$JWT"
    TOT="$(echo "$BODY" | grep -oE '"totalElements":[0-9]+' | grep -oE '[0-9]+' | head -1)"
    { [ -n "$TOT" ] && [ "$TOT" -ge 3 ]; } && ok "admin findAll thay don user khac (totalElements=$TOT)" || bad "admin findAll totalElements=$TOT"

    # cleanup: xoa don test + dia chi, khoi phuc ton kho
    dbsql "DELETE FROM lich_su_trang_thai_don_hang WHERE ma_don_hang > $BASELINE_SM;"
    dbsql "DELETE FROM chi_tiet_don_hang WHERE ma_don_hang > $BASELINE_SM;"
    dbsql "DELETE FROM don_hang WHERE ma_don_hang > $BASELINE_SM;"
    dbsql "UPDATE sach SET so_luong=$OLD_STOCK_SM WHERE ma_sach=$BOOK_SM;"
    curl -s -o /dev/null -X DELETE "$BASE/api/dia-chi/$ADDR_SM" -H "Authorization: Bearer $JWT"
  else
    bad "khong tao duoc dia chi cho section 6"
  fi
fi

echo "== Ket qua: PASS=$PASS FAIL=$FAIL =="
[ "$FAIL" -eq 0 ]
