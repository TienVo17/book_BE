#!/usr/bin/env bash
# Kiem tra huy don: hoan kho + hoan coupon + double-cancel 409 + phan quyen + doanh thu loai don huy.
# Yeu cau: smoke-setup.sh da chay (admin/Smoke@12345). Tu tao user fixture rieng (dang ky + kich
# hoat qua API), khong dung/sua mat khau cua user1/user2 nua. Tu don dep qua trap EXIT.
# Dung: ./scripts/kiem-tra-huy-don.sh [BASE_URL]
set -u
BASE="${1:-http://localhost:8080}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-Smoke@12345}"
DB_CONT="${DB_CONT:-web_ban_sach_db}"
BOOK="${BOOK:-1}"
RUN_ID="$(date +%s)-$$"
FIXTURE_PASS="HuyTest@12345"
PASS=0; FAIL=0
ok()  { PASS=$((PASS+1)); echo "  [PASS] $1"; }
bad() { FAIL=$((FAIL+1)); echo "  [FAIL] $1"; }
sql() { docker exec "$DB_CONT" mysql -uroot web_ban_sach -N -e "$1" 2>/dev/null; }
login() { curl -s -X POST "$BASE/tai-khoan/dang-nhap" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$1\",\"password\":\"$2\"}" | grep -oE 'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' | head -1; }
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

# Dang ky + kich hoat 1 user fixture rieng (khong dung seed user1/user2), tra ve JWT qua stdout.
# $1 = suffix duy nhat cho ten dang nhap/email cua fixture nay.
register_and_login() {
  local suffix="$1"
  local uname="huytest-${RUN_ID}-${suffix}"
  local email="huytest-${RUN_ID}-${suffix}@example.test"
  curl -s -o /dev/null -X POST "$BASE/tai-khoan/dang-ky" -H 'Content-Type: application/json' \
    -d "{\"tenDangNhap\":\"$uname\",\"matKhau\":\"$FIXTURE_PASS\",\"email\":\"$email\",\"hoDem\":\"Huy\",\"ten\":\"Test $suffix\",\"gioiTinh\":\"M\",\"soDienThoai\":\"0900000000\",\"diaChiMuaHang\":\"x\",\"diaChiGiaoHang\":\"x\"}"
  local ma_kich_hoat
  ma_kich_hoat="$(sql "SELECT ma_kich_hoat FROM nguoi_dung WHERE ten_dang_nhap='$uname';")"
  curl -s -o /dev/null "$BASE/tai-khoan/kich-hoat?email=$email&maKichHoat=$ma_kich_hoat"
  login "$uname" "$FIXTURE_PASS"
}

echo "== Kiem tra huy don @ $BASE =="

# --- Snapshot truoc khi mutate bat cu thu gi, va dang ky cleanup TRUOC mutation dau tien ---
BASELINE_ORDER="$(sql "SELECT COALESCE(MAX(ma_don_hang),0) FROM don_hang;")"
OLD_STOCK="$(sql "SELECT so_luong FROM sach WHERE ma_sach=$BOOK;")"

cleanup() {
  sql "DELETE FROM lich_su_trang_thai_don_hang WHERE ma_don_hang > $BASELINE_ORDER;"
  sql "DELETE FROM chi_tiet_don_hang WHERE ma_don_hang > $BASELINE_ORDER;"
  sql "DELETE FROM don_hang WHERE ma_don_hang > $BASELINE_ORDER;"
  sql "DELETE FROM coupon WHERE ma='HUYTEST';"
  sql "UPDATE sach SET so_luong=$OLD_STOCK WHERE ma_sach=$BOOK;"
  # Dia chi rieng cua admin (tao o Scenario D) khong khop pattern huytest-*, xoa rieng bang id.
  [ -n "${ADDR_ADMIN_ID:-}" ] && sql "DELETE FROM dia_chi_giao_hang WHERE ma_dia_chi=$ADDR_ADMIN_ID;"
  [ -n "${ADDR_ID:-}" ] && sql "DELETE FROM dia_chi_giao_hang WHERE ma_dia_chi=$ADDR_ID;"
  sql "DELETE FROM dia_chi_giao_hang WHERE ma_nguoi_dung IN (SELECT ma_nguoi_dung FROM nguoi_dung WHERE ten_dang_nhap LIKE 'huytest-${RUN_ID}-%');"
  sql "DELETE FROM nguoidung_quyen WHERE ma_nguoi_dung IN (SELECT ma_nguoi_dung FROM nguoi_dung WHERE ten_dang_nhap LIKE 'huytest-${RUN_ID}-%');"
  sql "DELETE FROM nguoi_dung WHERE ten_dang_nhap LIKE 'huytest-${RUN_ID}-%';"
}
trap cleanup EXIT

JWT_ADMIN="$(login "$ADMIN_USER" "$ADMIN_PASS")"
JWT_U1="$(register_and_login u1)"
JWT_U2="$(register_and_login u2)"
{ [ -n "$JWT_ADMIN" ] && [ -n "$JWT_U1" ] && [ -n "$JWT_U2" ]; } && ok "login admin + dang ky/kich hoat u1/u2" || { bad "login/dang ky that bai"; }

# Coupon test (FIXED, active)
sql "DELETE FROM coupon WHERE ma='HUYTEST';"
sql "INSERT INTO coupon (ma, loai, gia_tri_giam, gia_tri_toi_thieu, so_luong_toi_da, da_su_dung, is_active) VALUES ('HUYTEST','FIXED',1000,0,100,0,1);"
COUPON_ID="$(sql "SELECT ma_coupon FROM coupon WHERE ma='HUYTEST';")"

# Dia chi cho u1
ADDR="$(curl -s -X POST "$BASE/api/dia-chi" -H "Authorization: Bearer $JWT_U1" -H 'Content-Type: application/json' \
  -d '{"hoTen":"Huy Test","soDienThoai":"0900000000","diaChiDayDu":"Addr Huy","macDinh":false}')"
ADDR_ID="$(echo "$ADDR" | grep -oE '"maDiaChi":[0-9]+' | grep -oE '[0-9]+' | head -1)"
[ -n "$ADDR_ID" ] && ok "tao dia chi u1 (id=$ADDR_ID)" || bad "tao dia chi: $ADDR"

checkout_u1() { # $1=maCoupon(optional) -> echo order id (from checkout response maDonHang)
  local coupon="${1:-}"
  local body="{\"items\":[{\"maSach\":$BOOK,\"soLuong\":1}],\"maDiaChiGiaoHang\":$ADDR_ID,\"phuongThucThanhToan\":\"COD\""
  [ -n "$coupon" ] && body="$body,\"maCoupon\":\"$coupon\""
  body="$body}"
  curl -s -X POST "$BASE/api/don-hang/them" -H "Authorization: Bearer $JWT_U1" -H 'Content-Type: application/json' -d "$body" \
    | grep -oE '"maDonHang":[0-9]+' | grep -oE '[0-9]+' | head -1
}

# ===== Scenario A: checkout voi coupon -> huy -> hoan kho + hoan coupon; huy lan 2 -> 409 =====
sql "UPDATE sach SET so_luong=10 WHERE ma_sach=$BOOK;"
O1="$(checkout_u1 HUYTEST)"
ST_AFTER_CO="$(sql "SELECT so_luong FROM sach WHERE ma_sach=$BOOK;")"
CU_AFTER_CO="$(sql "SELECT da_su_dung FROM coupon WHERE ma='HUYTEST';")"
{ [ -n "$O1" ] && [ "$ST_AFTER_CO" = "9" ] && [ "$CU_AFTER_CO" = "1" ]; } \
  && ok "checkout coupon: kho 10->9, coupon da_su_dung=1 (order $O1)" \
  || bad "checkout coupon: order=$O1 stock=$ST_AFTER_CO daSuDung=$CU_AFTER_CO"

C_HUY="$(code -X POST "$BASE/api/don-hang/huy/$O1" -H "Authorization: Bearer $JWT_U1")"
ST_AFTER_HUY="$(sql "SELECT so_luong FROM sach WHERE ma_sach=$BOOK;")"
CU_AFTER_HUY="$(sql "SELECT da_su_dung FROM coupon WHERE ma='HUYTEST';")"
TT_HUY="$(sql "SELECT trang_thai_giao_hang FROM don_hang WHERE ma_don_hang=$O1;")"
[ "$C_HUY" = "200" ] && ok "u1 huy don -> 200" || bad "u1 huy don -> $C_HUY"
{ [ "$ST_AFTER_HUY" = "10" ] && [ "$CU_AFTER_HUY" = "0" ] && [ "$TT_HUY" = "3" ]; } \
  && ok "huy: kho hoan 9->10, coupon hoan ->0, trang thai=3" \
  || bad "huy: stock=$ST_AFTER_HUY daSuDung=$CU_AFTER_HUY tt=$TT_HUY"

C_HUY2="$(code -X POST "$BASE/api/don-hang/huy/$O1" -H "Authorization: Bearer $JWT_U1")"
[ "$C_HUY2" = "409" ] && ok "huy lan 2 -> 409" || bad "huy lan 2 -> $C_HUY2 (mong 409)"
ST_AFTER_HUY2="$(sql "SELECT so_luong FROM sach WHERE ma_sach=$BOOK;")"
[ "$ST_AFTER_HUY2" = "10" ] && ok "huy lan 2 KHONG hoan kho lai (van 10)" || bad "kho bi hoan 2 lan = $ST_AFTER_HUY2"

# ===== Scenario B: user khac huy -> 403 =====
O2="$(checkout_u1)"
C_403="$(code -X POST "$BASE/api/don-hang/huy/$O2" -H "Authorization: Bearer $JWT_U2")"
[ "$C_403" = "403" ] && ok "u2 huy don u1 -> 403" || bad "u2 huy don u1 -> $C_403 (mong 403)"

# ===== Scenario C: cap-nhat-trang-thai-giao-hang bang token user thuong -> 403 (hanh vi co san) =====
C_CN_USER="$(code -X POST "$BASE/api/don-hang/cap-nhat-trang-thai-giao-hang/$O2" -H "Authorization: Bearer $JWT_U1")"
[ "$C_CN_USER" = "403" ] && ok "cap-nhat bang token user -> 403 (khong vo)" || bad "cap-nhat user -> $C_CN_USER (mong 403)"

# ===== Scenario D: admin advance 2 lan tren don COD moi (checkout that qua authenticated flow) =====
# Guest endpoint /api/don-hang/them-don-hang-moi da bi xoa (Phase 2); tao don qua checkout that
# bang JWT cua admin, dia chi rieng cua admin.
ADDR_ADMIN="$(curl -s -X POST "$BASE/api/dia-chi" -H "Authorization: Bearer $JWT_ADMIN" -H 'Content-Type: application/json' \
  -d '{"hoTen":"Advance","soDienThoai":"0900000000","diaChiDayDu":"Addr Advance","macDinh":false}')"
ADDR_ADMIN_ID="$(echo "$ADDR_ADMIN" | grep -oE '"maDiaChi":[0-9]+' | grep -oE '[0-9]+' | head -1)"
[ -n "$ADDR_ADMIN_ID" ] && ok "tao dia chi admin (id=$ADDR_ADMIN_ID)" || bad "tao dia chi admin: $ADDR_ADMIN"

O3="$(curl -s -X POST "$BASE/api/don-hang/them" -H "Authorization: Bearer $JWT_ADMIN" -H 'Content-Type: application/json' \
  -d "{\"items\":[{\"maSach\":$BOOK,\"soLuong\":1}],\"maDiaChiGiaoHang\":$ADDR_ADMIN_ID,\"phuongThucThanhToan\":\"COD\"}" \
  | grep -oE '"maDonHang":[0-9]+' | grep -oE '[0-9]+' | head -1)"
[ -n "$O3" ] && ok "tao don COD cho admin qua checkout that (order $O3)" || bad "tao don admin that bai"
D1="$(code -X POST "$BASE/api/don-hang/cap-nhat-trang-thai-giao-hang/$O3" -H "Authorization: Bearer $JWT_ADMIN")"
D2="$(code -X POST "$BASE/api/don-hang/cap-nhat-trang-thai-giao-hang/$O3" -H "Authorization: Bearer $JWT_ADMIN")"
D3="$(code -X POST "$BASE/api/don-hang/cap-nhat-trang-thai-giao-hang/$O3" -H "Authorization: Bearer $JWT_ADMIN")"
{ [ "$D1" = "200" ] && [ "$D2" = "200" ] && [ "$D3" = "409" ]; } \
  && ok "admin advance COD: 200(0->1),200(1->2),409(cuoi)" || bad "admin advance codes: $D1,$D2,$D3"

# ===== Scenario E: admin thay don cua user khac (findAll/findById) =====
TOTAL="$(curl -s "$BASE/api/don-hang/findAll?page=0" -H "Authorization: Bearer $JWT_ADMIN" | grep -oE '"totalElements":[0-9]+' | grep -oE '[0-9]+' | head -1)"
{ [ -n "$TOTAL" ] && [ "$TOTAL" -ge 3 ]; } && ok "admin findAll thay don user khac (totalElements=$TOTAL >= 3)" || bad "admin findAll totalElements=$TOTAL (mong >=3)"
C_FIND1="$(code "$BASE/api/don-hang/2" -H "Authorization: Bearer $JWT_ADMIN")"  # order seed cua user khac
[ "$C_FIND1" = "200" ] && ok "admin findById don user khac -> 200" || bad "admin findById -> $C_FIND1 (mong 200)"

# ===== Scenario F: doanh thu KHONG tinh don da huy du da thanh toan =====
# Cot double NOT NULL o entity (DB cho phep NULL) -> phai set 0 nhu don that.
sql "INSERT INTO don_hang (ngay_tao, tong_tien_san_pham, chi_phi_giao_hang, chi_phi_thanh_toan, tong_tien, ho_ten, so_dien_thoai, trang_thai_thanh_toan, trang_thai_giao_hang, ma_nguoi_dung, version) VALUES (NOW(), 50000, 0, 0, 50000, 'Revenue Test', '0900000000', 1, 0, 2, 0);"
O_PAID="$(sql "SELECT MAX(ma_don_hang) FROM don_hang;")"
REV_BEFORE="$(curl -s "$BASE/api/admin/thong-ke" -H "Authorization: Bearer $JWT_ADMIN" | grep -oE '"totalRevenue":[0-9.]+' | grep -oE '[0-9.]+' | head -1)"
code -X POST "$BASE/api/don-hang/huy/$O_PAID" -H "Authorization: Bearer $JWT_ADMIN" >/dev/null
REV_AFTER="$(curl -s "$BASE/api/admin/thong-ke" -H "Authorization: Bearer $JWT_ADMIN" | grep -oE '"totalRevenue":[0-9.]+' | grep -oE '[0-9.]+' | head -1)"
DIFF="$(awk "BEGIN{printf \"%.0f\", $REV_BEFORE - $REV_AFTER}")"
[ "$DIFF" = "50000" ] && ok "doanh thu giam 50000 sau khi huy don da thanh toan ($REV_BEFORE -> $REV_AFTER)" || bad "doanh thu diff=$DIFF (mong 50000)"

# Cleanup chay tu dong qua trap EXIT (dang ky truoc mutation dau tien o tren).

echo "== Ket qua huy don: PASS=$PASS FAIL=$FAIL =="
[ "$FAIL" -eq 0 ]
