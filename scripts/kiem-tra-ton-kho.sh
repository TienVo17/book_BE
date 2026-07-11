#!/usr/bin/env bash
# Kiem tra tru kho nguyen tu khi checkout: race 2-request + rollback don da sach.
# Luoi verify LOCAL (thay cho TruKhoIT compile-only). Tu setup + tu don dep demo data.
# Dung: ./scripts/kiem-tra-ton-kho.sh [BASE_URL]   (mac dinh http://localhost:8080)
set -u
BASE="${1:-http://localhost:8080}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-Smoke@12345}"
DB_CONT="${DB_CONT:-web_ban_sach_db}"
BOOK="${BOOK:-1}"
BOOK2="${BOOK2:-2}"
PASS=0; FAIL=0
ok()  { PASS=$((PASS+1)); echo "  [PASS] $1"; }
bad() { FAIL=$((FAIL+1)); echo "  [FAIL] $1"; }
sql() { docker exec "$DB_CONT" mysql -uroot web_ban_sach -N -e "$1" 2>/dev/null; }

echo "== Kiem tra ton kho @ $BASE =="

# Login admin
JWT="$(curl -s -X POST "$BASE/tai-khoan/dang-nhap" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" \
  | grep -oE 'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' | head -1)"
[ -n "$JWT" ] && ok "login admin -> JWT" || { bad "login admin"; echo "PASS=$PASS FAIL=$FAIL"; exit 1; }

# Tao dia chi giao hang cho admin (seed khong co dia_chi_giao_hang row)
ADDR="$(curl -s -X POST "$BASE/api/dia-chi" -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  -d '{"hoTen":"Ton Kho Test","soDienThoai":"0900000000","diaChiDayDu":"Test Addr Ton Kho","macDinh":false}')"
ADDR_ID="$(echo "$ADDR" | grep -oE '"maDiaChi":[0-9]+' | grep -oE '[0-9]+' | head -1)"
[ -n "$ADDR_ID" ] && ok "tao dia chi (id=$ADDR_ID)" || { bad "tao dia chi: $ADDR"; echo "PASS=$PASS FAIL=$FAIL"; exit 1; }

# Snapshot ton kho de khoi phuc cuoi script
OLD_STOCK="$(sql "SELECT so_luong FROM sach WHERE ma_sach=$BOOK;")"
OLD_STOCK2="$(sql "SELECT so_luong FROM sach WHERE ma_sach=$BOOK2;")"

checkout_book() { # $1=maSach $2=qty -> echo HTTP code
  curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/don-hang/them" \
    -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
    -d "{\"items\":[{\"maSach\":$1,\"soLuong\":$2}],\"maDiaChiGiaoHang\":$ADDR_ID,\"phuongThucThanhToan\":\"COD\"}"
}

# --- Case 1: race 2 request cung sach ton=1 -> dung 1 thanh cong, kho=0 ---
sql "UPDATE sach SET so_luong=1 WHERE ma_sach=$BOOK;"
checkout_book "$BOOK" 1 > /tmp/tk_a &
checkout_book "$BOOK" 1 > /tmp/tk_b &
wait
CA="$(cat /tmp/tk_a)"; CB="$(cat /tmp/tk_b)"
FINAL="$(sql "SELECT so_luong FROM sach WHERE ma_sach=$BOOK;")"
if { [ "$CA" = "200" ] && [ "$CB" = "400" ]; } || { [ "$CA" = "400" ] && [ "$CB" = "200" ]; }; then
  ok "race: dung 1x200 + 1x400 (got $CA,$CB)"
else
  bad "race: codes bat thuong ($CA,$CB) - mong 1x200 1x400"
fi
[ "$FINAL" = "0" ] && ok "ton kho cuoi = 0" || bad "ton kho cuoi = $FINAL (mong 0)"

# --- Case 2: don 2 sach, 1 sach thieu -> 400 va ca 2 khong doi (rollback) ---
sql "UPDATE sach SET so_luong=0 WHERE ma_sach=$BOOK;"    # X het hang
sql "UPDATE sach SET so_luong=5 WHERE ma_sach=$BOOK2;"   # Y du hang
CODE="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/don-hang/them" \
  -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  -d "{\"items\":[{\"maSach\":$BOOK,\"soLuong\":1},{\"maSach\":$BOOK2,\"soLuong\":1}],\"maDiaChiGiaoHang\":$ADDR_ID,\"phuongThucThanhToan\":\"COD\"}")"
Y="$(sql "SELECT so_luong FROM sach WHERE ma_sach=$BOOK2;")"
[ "$CODE" = "400" ] && ok "don 2 sach (1 thieu) -> 400" || bad "code $CODE (mong 400)"
[ "$Y" = "5" ] && ok "rollback: ton kho sach du van = 5" || bad "ton kho sach du = $Y (mong 5 - rollback that bai)"

# --- Cleanup: xoa don + dia chi test, khoi phuc ton kho demo ---
sql "DELETE ct FROM chi_tiet_don_hang ct JOIN don_hang d ON ct.ma_don_hang=d.ma_don_hang WHERE d.ma_nguoi_dung=1;"
sql "DELETE FROM lich_su_trang_thai_don_hang WHERE ma_don_hang IN (SELECT ma_don_hang FROM don_hang WHERE ma_nguoi_dung=1);"
sql "DELETE FROM don_hang WHERE ma_nguoi_dung=1;"
sql "UPDATE sach SET so_luong=$OLD_STOCK WHERE ma_sach=$BOOK;"
sql "UPDATE sach SET so_luong=$OLD_STOCK2 WHERE ma_sach=$BOOK2;"
curl -s -o /dev/null -X DELETE "$BASE/api/dia-chi/$ADDR_ID" -H "Authorization: Bearer $JWT"

echo "== Ket qua ton kho: PASS=$PASS FAIL=$FAIL =="
[ "$FAIL" -eq 0 ]
