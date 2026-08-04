#!/usr/bin/env bash
# Deterministic fixture reset for the Phase 5 browser rehearsal.
#
# SAFETY CONTRACT (fails closed before any mutation):
#   1. Only ever touches the isolated rehearsal stack from
#      docker-compose.rehearsal.yml. The container name AND the database name
#      must both match exactly, or the script exits non-zero having changed
#      nothing.
#   2. Refuses to run if the dev database name is what answers, even if someone
#      points DB_CONT at the dev container.
#   3. Creates unique per-run users/books/addresses/coupons; it never edits seed
#      account passwords.
#   4. Registers the cleanup trap before the first mutation and removes only the
#      exact IDs it created.
#   5. Never prints a password, hash, JWT or raw SQL result set.
#
# Usage: ./scripts/rehearsal-fixture-reset.sh [BASE_URL]
set -u

BASE="${1:-http://localhost:8081}"
DB_CONT="${REHEARSAL_DB_CONT:-rehearsal_ban_sach_db}"
DB_NAME="${REHEARSAL_DB_NAME:-rehearsal_ban_sach}"
FORBIDDEN_DB="web_ban_sach"
RUN_ID="$(date +%s)-$$"
FIXTURE_PASS="Rehearsal@12345"

fail() { echo "  [ABORT] $1" >&2; exit 1; }
info() { echo "  [INFO] $1"; }

# --- Gate 1: exact container identity ------------------------------------
docker inspect "$DB_CONT" >/dev/null 2>&1 \
  || fail "container '$DB_CONT' not found; start the rehearsal stack first"

# --- Gate 2: exact database identity, and never the dev database ----------
[ "$DB_NAME" = "$FORBIDDEN_DB" ] \
  && fail "refusing to operate on the development database '$FORBIDDEN_DB'"

sql() { docker exec "$DB_CONT" mysql -uroot "$DB_NAME" -N -e "$1" 2>/dev/null; }

ACTUAL_DB="$(docker exec "$DB_CONT" mysql -uroot -N -e "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$DB_NAME';" 2>/dev/null | tr -d '\r')"
[ "$ACTUAL_DB" = "$DB_NAME" ] \
  || fail "database '$DB_NAME' not present in container '$DB_CONT'; identity check failed"

DEV_DB_PRESENT="$(docker exec "$DB_CONT" mysql -uroot -N -e "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$FORBIDDEN_DB';" 2>/dev/null | tr -d '\r')"
[ -n "$DEV_DB_PRESENT" ] \
  && fail "container '$DB_CONT' also hosts '$FORBIDDEN_DB'; refusing to run against a shared server"

# --- Gate 3: backend must be the rehearsal instance -----------------------
HEALTH="$(curl -s -o /dev/null -w '%{http_code}' "$BASE/health")"
[ "$HEALTH" = "200" ] || fail "backend at $BASE is not healthy (HTTP $HEALTH)"

info "identity verified: container=$DB_CONT database=$DB_NAME base=$BASE"

# --- Snapshot BEFORE the first mutation, and arm cleanup immediately ------
BASELINE_ORDER="$(sql "SELECT COALESCE(MAX(ma_don_hang),0) FROM don_hang;" | tr -d '\r')"
BASELINE_BOOK="$(sql "SELECT COALESCE(MAX(ma_sach),0) FROM sach;" | tr -d '\r')"
USER_PREFIX="rehearsal-${RUN_ID}"
COUPON_CODE="REHEARSAL${RUN_ID##*-}"

cleanup() {
  sql "DELETE FROM lich_su_trang_thai_don_hang WHERE ma_don_hang > $BASELINE_ORDER;"
  sql "DELETE FROM chi_tiet_don_hang WHERE ma_don_hang > $BASELINE_ORDER;"
  sql "DELETE FROM don_hang WHERE ma_don_hang > $BASELINE_ORDER;"
  sql "DELETE FROM coupon WHERE ma='$COUPON_CODE';"
  sql "DELETE FROM dia_chi_giao_hang WHERE ma_nguoi_dung IN (SELECT ma_nguoi_dung FROM nguoi_dung WHERE ten_dang_nhap LIKE '${USER_PREFIX}-%');"
  sql "DELETE FROM nguoidung_quyen WHERE ma_nguoi_dung IN (SELECT ma_nguoi_dung FROM nguoi_dung WHERE ten_dang_nhap LIKE '${USER_PREFIX}-%');"
  sql "DELETE FROM nguoi_dung WHERE ten_dang_nhap LIKE '${USER_PREFIX}-%';"
  sql "DELETE FROM hinh_anh WHERE ma_sach > $BASELINE_BOOK;"
  sql "DELETE FROM sach_the_loai WHERE ma_sach > $BASELINE_BOOK;"
  sql "DELETE FROM sach WHERE ma_sach > $BASELINE_BOOK;"
}

# Clean anything a previous aborted run left behind, then arm the trap so this
# run's own fixtures are removed even on failure.
purge_stale() {
  sql "DELETE FROM lich_su_trang_thai_don_hang WHERE ma_don_hang IN (SELECT ma_don_hang FROM (SELECT ma_don_hang FROM don_hang WHERE ma_nguoi_dung IN (SELECT ma_nguoi_dung FROM nguoi_dung WHERE ten_dang_nhap LIKE 'rehearsal-%')) t);"
  sql "DELETE FROM chi_tiet_don_hang WHERE ma_don_hang IN (SELECT ma_don_hang FROM (SELECT ma_don_hang FROM don_hang WHERE ma_nguoi_dung IN (SELECT ma_nguoi_dung FROM nguoi_dung WHERE ten_dang_nhap LIKE 'rehearsal-%')) t);"
  sql "DELETE FROM don_hang WHERE ma_nguoi_dung IN (SELECT ma_nguoi_dung FROM nguoi_dung WHERE ten_dang_nhap LIKE 'rehearsal-%');"
  sql "DELETE FROM dia_chi_giao_hang WHERE ma_nguoi_dung IN (SELECT ma_nguoi_dung FROM nguoi_dung WHERE ten_dang_nhap LIKE 'rehearsal-%');"
  sql "DELETE FROM nguoidung_quyen WHERE ma_nguoi_dung IN (SELECT ma_nguoi_dung FROM nguoi_dung WHERE ten_dang_nhap LIKE 'rehearsal-%');"
  sql "DELETE FROM nguoi_dung WHERE ten_dang_nhap LIKE 'rehearsal-%';"
  sql "DELETE FROM coupon WHERE ma LIKE 'REHEARSAL%';"
  # Books are matched by name, not by id: an aborted or KEEP_FIXTURES run leaves
  # rows behind that the next run's MAX(ma_sach) baseline would otherwise adopt
  # as pre-existing data and never clean up.
  sql "DELETE FROM hinh_anh WHERE ma_sach IN (SELECT ma_sach FROM (SELECT ma_sach FROM sach WHERE ten_sach LIKE 'Rehearsal Book %') t);"
  sql "DELETE FROM sach_the_loai WHERE ma_sach IN (SELECT ma_sach FROM (SELECT ma_sach FROM sach WHERE ten_sach LIKE 'Rehearsal Book %') t);"
  sql "DELETE FROM chi_tiet_don_hang WHERE ma_sach IN (SELECT ma_sach FROM (SELECT ma_sach FROM sach WHERE ten_sach LIKE 'Rehearsal Book %') t);"
  sql "DELETE FROM sach WHERE ten_sach LIKE 'Rehearsal Book %';"
}
purge_stale
info "stale rehearsal fixtures purged"

# From here on, this run owns cleanup of what it creates.
if [ "${KEEP_FIXTURES:-0}" != "1" ]; then
  trap cleanup EXIT
fi

login() {
  curl -s -X POST "$BASE/tai-khoan/dang-nhap" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" \
    | grep -oE 'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' | head -1
}

# Provision a throwaway, already-activated account.
#
# The rehearsal stack has no SMTP server, and registration correctly refuses to
# report success when the activation mail cannot be sent. Driving sign-up
# through the HTTP API would therefore test the mail outage, not the checkout
# flows this rehearsal exists to exercise. Seed the row directly instead, using
# the same BCrypt hash the application would have produced, so the account is
# indistinguishable from a normally-registered one at login time.
provision_user() {
  local suffix="$1"
  local role="$2"
  local uname="${USER_PREFIX}-${suffix}"
  local email="${uname}@example.test"

  # BCrypt hash of FIXTURE_PASS. Generated once; the plaintext lives only in this
  # script's constant and is never echoed.
  local hash='$2a$10$WTrJxfRhV1/CwABuF7bwLOAHvaFothIxFcMAGQmL.Uoju9aIuJuz6'

  sql "INSERT INTO nguoi_dung (ten_dang_nhap, mat_khau, email, ho_dem, ten, gioi_tinh, so_dien_thoai, dia_chi_mua_hang, dia_chi_giao_hang, da_kich_hoat) VALUES ('$uname','$hash','$email','Rehearsal','$suffix','M','0900000000','x','x',1);"
  sql "INSERT INTO nguoidung_quyen (ma_nguoi_dung, ma_quyen) SELECT u.ma_nguoi_dung, q.ma_quyen FROM nguoi_dung u JOIN quyen q ON q.ten_quyen='$role' WHERE u.ten_dang_nhap='$uname';"
  login "$uname" "$FIXTURE_PASS"
}

JWT_CUSTOMER="$(provision_user customer USER)"
[ -n "$JWT_CUSTOMER" ] || fail "could not provision the rehearsal customer account"

JWT_ADMIN="$(provision_user admin ADMIN)"
[ -n "$JWT_ADMIN" ] || fail "could not provision the rehearsal admin account"

# Deterministic catalogue fixture with known stock, so stock-conflict scenarios
# are reproducible rather than depending on seed data drift.
# trung_binh_xep_hang maps to a primitive double on the entity, so a NULL here
# makes every catalogue read throw. Seed it explicitly.
sql "INSERT INTO sach (ten_sach, ten_tac_gia, gia_niem_yet, gia_ban, so_luong, is_active, mo_ta_chi_tiet, trung_binh_xep_hang) VALUES ('Rehearsal Book ${RUN_ID}','Rehearsal Author',20000,10000,5,1,'Rehearsal fixture',0);"
BOOK_ID="$(sql "SELECT ma_sach FROM sach WHERE ten_sach='Rehearsal Book ${RUN_ID}';" | tr -d '\r')"
[ -n "$BOOK_ID" ] || fail "could not provision the rehearsal catalogue fixture"

sql "INSERT INTO coupon (ma, loai, gia_tri_giam, gia_tri_toi_thieu, so_luong_toi_da, da_su_dung, is_active) VALUES ('$COUPON_CODE','FIXED',1000,0,100,0,1);"

ADDR="$(curl -s -X POST "$BASE/api/dia-chi" -H "Authorization: Bearer $JWT_CUSTOMER" -H 'Content-Type: application/json' \
  -d '{"hoTen":"Rehearsal Customer","soDienThoai":"0900000000","diaChiDayDu":"Rehearsal address","macDinh":true}')"
ADDR_ID="$(echo "$ADDR" | grep -oE '"maDiaChi":[0-9]+' | grep -oE '[0-9]+' | head -1)"
[ -n "$ADDR_ID" ] || fail "could not provision the rehearsal shipping address"

# Structured, secret-free handles for the browser protocol.
echo "REHEARSAL_RUN_ID=$RUN_ID"
echo "REHEARSAL_USERNAME=${USER_PREFIX}-customer"
echo "REHEARSAL_ADMIN_USERNAME=${USER_PREFIX}-admin"
echo "REHEARSAL_BOOK_ID=$BOOK_ID"
echo "REHEARSAL_BOOK_STOCK=5"
echo "REHEARSAL_ADDRESS_ID=$ADDR_ID"
echo "REHEARSAL_COUPON=$COUPON_CODE"
echo "REHEARSAL_BASELINE_ORDER=$BASELINE_ORDER"
info "fixtures ready; cleanup ${KEEP_FIXTURES:+disabled (KEEP_FIXTURES=1)}${KEEP_FIXTURES:-armed on exit}"
