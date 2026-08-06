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
#   6. Never reports success over a failed statement. Every mysql invocation is
#      checked; a failure during provisioning aborts, and a failure during
#      cleanup makes the script exit non-zero after attempting the rest of the
#      deletes. Evidence that says "residue 0" must mean the deletes actually
#      ran, not that their errors went to /dev/null.
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

# Statement errors are surfaced, not swallowed. Only the mysql diagnostic is
# printed, with anything hash- or token-shaped redacted, because a syntax error
# echoes back a fragment of the failing statement -- and one of those statements
# carries the fixture BCrypt hash.
SQL_ERR_FILE="$(mktemp)"
SQL_STRICT=1
SQL_FAILURES=0
trap 'rm -f "$SQL_ERR_FILE"' EXIT

redact() {
  sed -E 's/\$2[aby]\$[0-9]{2}\$[A-Za-z0-9./]{20,}/[REDACTED-HASH]/g; s/eyJ[A-Za-z0-9_.-]{20,}/[REDACTED-TOKEN]/g'
}

sql() {
  local out rc
  out="$(docker exec "$DB_CONT" mysql -uroot "$DB_NAME" -N -e "$1" 2>"$SQL_ERR_FILE")"
  rc=$?
  if [ "$rc" -ne 0 ]; then
    SQL_FAILURES=$((SQL_FAILURES + 1))
    echo "  [SQL-FAIL] $(head -2 "$SQL_ERR_FILE" | redact | tr '\n' ' ')" >&2
    [ "$SQL_STRICT" = "1" ] && fail "aborting on a failed statement; nothing further was attempted"
    return "$rc"
  fi
  printf '%s' "$out"
}

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
# A value-returning statement runs in a subshell, so its abort cannot stop this
# one. Check the values instead: an empty baseline would make every cleanup
# predicate below a syntax error, i.e. no cleanup at all.
case "$BASELINE_ORDER" in ''|*[!0-9]*) fail "could not read the order id baseline" ;; esac
case "$BASELINE_BOOK" in ''|*[!0-9]*) fail "could not read the book id baseline" ;; esac
USER_PREFIX="rehearsal-${RUN_ID}"
COUPON_CODE="REHEARSAL${RUN_ID##*-}"

# Residue is counted from the same table set the cleanup deletes from. A run that
# reports zero here has proven the deletes ran; a run that cannot even count has
# proven nothing and says so.
dem_residue() {
  sql "SELECT (SELECT COUNT(*) FROM sach WHERE ten_sach LIKE 'Rehearsal Book %')
            + (SELECT COUNT(*) FROM nguoi_dung WHERE ten_dang_nhap LIKE 'rehearsal-%')
            + (SELECT COUNT(*) FROM coupon WHERE ma LIKE 'REHEARSAL%')
            + (SELECT COUNT(*) FROM don_hang WHERE ma_don_hang > $BASELINE_ORDER)
            + (SELECT COUNT(*) FROM danhgia WHERE ma_don_hang > $BASELINE_ORDER);" | tr -d '\r'
}

cleanup() {
  # Best-effort from here: keep deleting even after a statement fails, then
  # report. Exiting at the first error would leave MORE residue behind.
  SQL_STRICT=0

  # Reviews first, and this ordering is now mandatory rather than tidy:
  # V15 gave danhgia.ma_don_hang a foreign key to don_hang, so deleting the order
  # first fails outright instead of leaving a dangling reference.
  # danhgia_an_tombstone needs no clause here: it cascades from nguoi_dung and sach.
  sql "DELETE FROM danhgia WHERE ma_don_hang > $BASELINE_ORDER;"
  sql "DELETE FROM danhgia WHERE ma_sach > $BASELINE_BOOK;"
  sql "DELETE FROM danhgia WHERE ma_nguoi_dung IN (SELECT ma_nguoi_dung FROM nguoi_dung WHERE ten_dang_nhap LIKE '${USER_PREFIX}-%');"
  sql "DELETE FROM lich_su_trang_thai_don_hang WHERE ma_don_hang > $BASELINE_ORDER;"
  sql "DELETE FROM chi_tiet_don_hang WHERE ma_don_hang > $BASELINE_ORDER;"
  sql "DELETE FROM don_hang WHERE ma_don_hang > $BASELINE_ORDER;"
  sql "DELETE FROM coupon WHERE ma='$COUPON_CODE';"
  sql "DELETE FROM dia_chi_giao_hang WHERE ma_nguoi_dung IN (SELECT ma_nguoi_dung FROM nguoi_dung WHERE ten_dang_nhap LIKE '${USER_PREFIX}-%');"
  sql "DELETE FROM nguoidung_quyen WHERE ma_nguoi_dung IN (SELECT ma_nguoi_dung FROM nguoi_dung WHERE ten_dang_nhap LIKE '${USER_PREFIX}-%');"
  sql "DELETE FROM nguoi_dung WHERE ten_dang_nhap LIKE '${USER_PREFIX}-%';"
  sql "DELETE FROM hinh_anh WHERE ma_sach > $BASELINE_BOOK;"
  sql "DELETE FROM sach_theloai WHERE ma_sach > $BASELINE_BOOK;"
  sql "DELETE FROM sach WHERE ma_sach > $BASELINE_BOOK;"

  local residue
  residue="$(dem_residue)"
  if [ -z "$residue" ]; then
    echo "  [ABORT] cleanup could not verify residue; treat this run as dirty" >&2
    exit 1
  fi
  if [ "$residue" != "0" ] || [ "$SQL_FAILURES" -ne 0 ]; then
    echo "  [ABORT] cleanup incomplete: residue=$residue failed_statements=$SQL_FAILURES" >&2
    exit 1
  fi
  info "cleanup verified: residue=0"
}

# Clean anything a previous aborted run left behind, then arm the trap so this
# run's own fixtures are removed even on failure.
purge_stale() {
  sql "DELETE FROM danhgia WHERE ma_nguoi_dung IN (SELECT ma_nguoi_dung FROM nguoi_dung WHERE ten_dang_nhap LIKE 'rehearsal-%');"
  sql "DELETE FROM danhgia WHERE ma_sach IN (SELECT ma_sach FROM (SELECT ma_sach FROM sach WHERE ten_sach LIKE 'Rehearsal Book %') t);"
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
  sql "DELETE FROM sach_theloai WHERE ma_sach IN (SELECT ma_sach FROM (SELECT ma_sach FROM sach WHERE ten_sach LIKE 'Rehearsal Book %') t);"
  sql "DELETE FROM chi_tiet_don_hang WHERE ma_sach IN (SELECT ma_sach FROM (SELECT ma_sach FROM sach WHERE ten_sach LIKE 'Rehearsal Book %') t);"
  sql "DELETE FROM sach WHERE ten_sach LIKE 'Rehearsal Book %';"
}
purge_stale
info "stale rehearsal fixtures purged"

# From here on, this run owns cleanup of what it creates.
if [ "${KEEP_FIXTURES:-0}" != "1" ]; then
  trap 'cleanup; rm -f "$SQL_ERR_FILE"' EXIT
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
