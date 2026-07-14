#!/usr/bin/env bash
# HTTP/runtime smoke cho contract ton kho delta. Can stack Docker da chay va credential smoke local hop le.
# Script chi dung fixture theo run ID, cleanup dung exact ID/ownership proof, va KHONG thay the integration test concurrency co barrier.
set -Eeuo pipefail

BASE="${1:-http://localhost:8080}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-Smoke@12345}"
DB_CONT="${DB_CONT:-web_ban_sach_db}"
DB_NAME="${DB_NAME:-web_ban_sach}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"
RUN_ID="$(date +%s)_$$_${RANDOM}"
BOOK_TITLE="StockDelta_${RUN_ID}"
BOOK_SLUG="stock-delta-${RUN_ID//_/-}"
ADDRESS_TEXT="StockDeltaAddr_${RUN_ID}"
USER_USER="stockdelta_${RUN_ID}"
USER_EMAIL="stockdelta_${RUN_ID}@example.invalid"
USER_PASS="$ADMIN_PASS"
TMP_DIR="$(mktemp -d)"
BODY_FILE="$TMP_DIR/body"
HEADER_FILE="$TMP_DIR/headers"
CODE=""
BODY=""
JWT=""
USER_JWT=""
ADMIN_ID=""
USER_ID=""
USER_ROLE_ID=""
BOOK_ID=""
ADDRESS_ID=""
ORDER_IDS=()
USER_CREATE_ATTEMPTED=0
BOOK_CREATE_ATTEMPTED=0
ADDRESS_CREATE_ATTEMPTED=0
CHECKOUT_ATTEMPTS=0
PASS=0
FAIL=0
PID_CHECKOUT=""
PID_DELTA=""
PID_CANCEL=""

if [[ ! "$RUN_ID" =~ ^[0-9_]+$ ]] \
    || [[ ! "$ADMIN_USER" =~ ^[A-Za-z0-9_.-]+$ ]] \
    || [[ ! "$USER_USER" =~ ^[A-Za-z0-9_.-]+$ ]] \
    || [[ ! "$USER_EMAIL" =~ ^[A-Za-z0-9_@.-]+$ ]]; then
  echo "Run ID hoac username khong hop le" >&2
  rm -rf "$TMP_DIR"
  exit 1
fi

sql() {
  docker exec -e MYSQL_PWD="$DB_PASSWORD" "$DB_CONT" \
    mysql -u"$DB_USER" "$DB_NAME" --batch --skip-column-names -e "$1" 2>/dev/null
}

is_numeric_id() {
  [[ "${1:-}" =~ ^[1-9][0-9]*$ ]]
}

append_order_id() {
  local id="${1:-}" existing
  is_numeric_id "$id" || return 1
  for existing in "${ORDER_IDS[@]:-}"; do
    [[ "$existing" = "$id" ]] && return 0
  done
  ORDER_IDS+=("$id")
}

join_numeric_ids() {
  local result="" id
  for id in "$@"; do
    is_numeric_id "$id" || return 1
    result+="${result:+,}$id"
  done
  printf '%s' "$result"
}

discover_unique_id() {
  local count_query="$1" id_query="$2" count candidate
  count="$(sql "$count_query")" || return 1
  [[ "$count" = "1" ]] || return 1
  candidate="$(sql "$id_query")" || return 1
  is_numeric_id "$candidate" || return 1
  printf '%s' "$candidate"
}

cleanup() {
  local original_status="${1:-1}" cleanup_failed=0 ids="" remaining="" discovered="" candidate="" proof=""
  local user_owned=0 book_owned=0 address_owned=0 id
  local -a verified_order_ids=()
  trap - EXIT INT TERM
  set +e

  # Reap curl background (moi curl co max-time) truoc discovery de khong co mutation den sau cleanup.
  # Khong kill curl: mat ket noi client khong dam bao server dung xu ly request da nhan.
  for candidate in "$PID_CHECKOUT" "$PID_DELTA" "$PID_CANCEL"; do
    if is_numeric_id "$candidate" && kill -0 "$candidate" 2>/dev/null; then
      wait "$candidate" 2>/dev/null
    fi
  done

  # Recovery chi xoa khi co ownership proof: marker unique cua user/sach, principal va exact IDs.
  if [[ "$USER_CREATE_ATTEMPTED" -eq 1 ]] && ! is_numeric_id "$USER_ID"; then
    if candidate="$(discover_unique_id \
        "SELECT COUNT(*) FROM nguoi_dung WHERE ten_dang_nhap='$USER_USER' AND email='$USER_EMAIL';" \
        "SELECT ma_nguoi_dung FROM nguoi_dung WHERE ten_dang_nhap='$USER_USER' AND email='$USER_EMAIL';")"; then
      USER_ID="$candidate"
    else
      cleanup_failed=1
    fi
  fi

  if [[ "$USER_CREATE_ATTEMPTED" -eq 1 ]] && is_numeric_id "$USER_ID"; then
    proof="$(sql "SELECT COUNT(*) FROM nguoi_dung
        WHERE ma_nguoi_dung=$USER_ID
          AND ten_dang_nhap='$USER_USER'
          AND email='$USER_EMAIL';")" || cleanup_failed=1
    [[ "$proof" = "1" ]] && user_owned=1 || cleanup_failed=1
  fi

  if [[ "$BOOK_CREATE_ATTEMPTED" -eq 1 ]] && ! is_numeric_id "$BOOK_ID"; then
    if candidate="$(discover_unique_id \
        "SELECT COUNT(*) FROM sach WHERE slug='$BOOK_SLUG';" \
        "SELECT ma_sach FROM sach WHERE slug='$BOOK_SLUG';")"; then
      BOOK_ID="$candidate"
    else
      cleanup_failed=1
    fi
  fi

  if [[ "$ADDRESS_CREATE_ATTEMPTED" -eq 1 ]] && ! is_numeric_id "$ADDRESS_ID"; then
    if is_numeric_id "$ADMIN_ID" && candidate="$(discover_unique_id \
        "SELECT COUNT(*) FROM dia_chi_giao_hang WHERE dia_chi_day_du='$ADDRESS_TEXT' AND ma_nguoi_dung=$ADMIN_ID;" \
        "SELECT ma_dia_chi FROM dia_chi_giao_hang WHERE dia_chi_day_du='$ADDRESS_TEXT' AND ma_nguoi_dung=$ADMIN_ID;")"; then
      ADDRESS_ID="$candidate"
    else
      cleanup_failed=1
    fi
  fi

  # Re-prove ownership cho ca ID lay tu HTTP; khong tin ID chi vi response co dang numeric.
  if is_numeric_id "$BOOK_ID"; then
    proof="$(sql "SELECT COUNT(*) FROM sach WHERE ma_sach=$BOOK_ID AND slug='$BOOK_SLUG';")" || cleanup_failed=1
    [[ "$proof" = "1" ]] && book_owned=1 || cleanup_failed=1
  fi
  if is_numeric_id "$ADDRESS_ID" && is_numeric_id "$ADMIN_ID"; then
    proof="$(sql "SELECT COUNT(*) FROM dia_chi_giao_hang
        WHERE ma_dia_chi=$ADDRESS_ID
          AND ma_nguoi_dung=$ADMIN_ID
          AND dia_chi_day_du='$ADDRESS_TEXT';")" || cleanup_failed=1
    [[ "$proof" = "1" ]] && address_owned=1 || cleanup_failed=1
  fi

  # Moi order tim lai phai thuoc principal + marker dia chi + sach co slug unique cua run.
  if [[ "$CHECKOUT_ATTEMPTS" -gt 0 ]]; then
    if [[ "$book_owned" -eq 1 ]] && is_numeric_id "$ADMIN_ID"; then
      if discovered="$(sql "SELECT DISTINCT d.ma_don_hang
          FROM don_hang d
          JOIN chi_tiet_don_hang c ON c.ma_don_hang=d.ma_don_hang
          JOIN sach s ON s.ma_sach=c.ma_sach
          WHERE d.ma_nguoi_dung=$ADMIN_ID
            AND d.dia_chi_nhan_hang='$ADDRESS_TEXT'
            AND c.ma_sach=$BOOK_ID
            AND s.slug='$BOOK_SLUG';")"; then
        while IFS= read -r candidate; do
          [[ -z "$candidate" ]] && continue
          append_order_id "$candidate" || cleanup_failed=1
        done <<< "$discovered"
      else
        cleanup_failed=1
      fi
    else
      cleanup_failed=1
    fi
  fi

  # Re-prove tung captured order truoc khi xoa; candidate khong dat proof duoc giu nguyen.
  if ((${#ORDER_IDS[@]} > 0)); then
    if [[ "$book_owned" -eq 1 ]] && is_numeric_id "$ADMIN_ID"; then
      for id in "${ORDER_IDS[@]}"; do
        is_numeric_id "$id" || { cleanup_failed=1; continue; }
        proof="$(sql "SELECT COUNT(*) FROM don_hang d
            WHERE d.ma_don_hang=$id
              AND d.ma_nguoi_dung=$ADMIN_ID
              AND d.dia_chi_nhan_hang='$ADDRESS_TEXT'
              AND EXISTS (
                SELECT 1 FROM chi_tiet_don_hang c
                JOIN sach s ON s.ma_sach=c.ma_sach
                WHERE c.ma_don_hang=d.ma_don_hang
                  AND c.ma_sach=$BOOK_ID
                  AND s.slug='$BOOK_SLUG'
              );")" || cleanup_failed=1
        if [[ "$proof" = "1" ]]; then
          verified_order_ids+=("$id")
        else
          cleanup_failed=1
        fi
      done
    else
      cleanup_failed=1
    fi
  fi

  if ((${#verified_order_ids[@]} > 0)); then
    ids="$(join_numeric_ids "${verified_order_ids[@]}")" || cleanup_failed=1
    if [[ -n "$ids" ]]; then
      sql "DELETE l FROM lich_su_trang_thai_don_hang l
          JOIN don_hang d ON d.ma_don_hang=l.ma_don_hang
          WHERE l.ma_don_hang IN ($ids)
            AND d.ma_nguoi_dung=$ADMIN_ID
            AND d.dia_chi_nhan_hang='$ADDRESS_TEXT'
            AND EXISTS (
              SELECT 1 FROM chi_tiet_don_hang c
              JOIN sach s ON s.ma_sach=c.ma_sach
              WHERE c.ma_don_hang=d.ma_don_hang
                AND c.ma_sach=$BOOK_ID
                AND s.slug='$BOOK_SLUG'
            );" || cleanup_failed=1
      sql "DELETE c FROM chi_tiet_don_hang c
          JOIN don_hang d ON d.ma_don_hang=c.ma_don_hang
          JOIN sach s ON s.ma_sach=c.ma_sach
          WHERE c.ma_don_hang IN ($ids)
            AND d.ma_nguoi_dung=$ADMIN_ID
            AND d.dia_chi_nhan_hang='$ADDRESS_TEXT'
            AND c.ma_sach=$BOOK_ID
            AND s.slug='$BOOK_SLUG';" || cleanup_failed=1
      sql "DELETE FROM don_hang
          WHERE ma_don_hang IN ($ids)
            AND ma_nguoi_dung=$ADMIN_ID
            AND dia_chi_nhan_hang='$ADDRESS_TEXT';" || cleanup_failed=1
      remaining="$(sql "SELECT COUNT(*) FROM don_hang WHERE ma_don_hang IN ($ids);")" || cleanup_failed=1
      [[ "$remaining" = "0" ]] || cleanup_failed=1
    fi
  fi

  if [[ "$address_owned" -eq 1 ]]; then
    sql "DELETE FROM dia_chi_giao_hang
        WHERE ma_dia_chi=$ADDRESS_ID
          AND ma_nguoi_dung=$ADMIN_ID
          AND dia_chi_day_du='$ADDRESS_TEXT';" || cleanup_failed=1
    remaining="$(sql "SELECT COUNT(*) FROM dia_chi_giao_hang WHERE ma_dia_chi=$ADDRESS_ID;")" || cleanup_failed=1
    [[ "$remaining" = "0" ]] || cleanup_failed=1
  fi

  if [[ "$book_owned" -eq 1 ]]; then
    sql "DELETE FROM hinh_anh WHERE ma_sach=$BOOK_ID
        AND EXISTS (SELECT 1 FROM sach s WHERE s.ma_sach=$BOOK_ID AND s.slug='$BOOK_SLUG');" || cleanup_failed=1
    sql "DELETE FROM sach_thong_tin_chi_tiet WHERE ma_sach=$BOOK_ID
        AND EXISTS (SELECT 1 FROM sach s WHERE s.ma_sach=$BOOK_ID AND s.slug='$BOOK_SLUG');" || cleanup_failed=1
    sql "DELETE FROM sach_theloai WHERE ma_sach=$BOOK_ID
        AND EXISTS (SELECT 1 FROM sach s WHERE s.ma_sach=$BOOK_ID AND s.slug='$BOOK_SLUG');" || cleanup_failed=1
    # Script khong tao cart/wishlist/review. Neu concurrent user gan row vao fixture,
    # de FK chan xoa sach thay vi xoa du lieu ngoai ownership cua run.
    sql "DELETE FROM sach WHERE ma_sach=$BOOK_ID AND slug='$BOOK_SLUG';" || cleanup_failed=1
    remaining="$(sql "SELECT COUNT(*) FROM sach WHERE ma_sach=$BOOK_ID;")" || cleanup_failed=1
    [[ "$remaining" = "0" ]] || cleanup_failed=1
  fi

  if [[ "$user_owned" -eq 1 ]]; then
    sql "DELETE nq FROM nguoidung_quyen nq
        JOIN nguoi_dung n ON n.ma_nguoi_dung=nq.ma_nguoi_dung
        WHERE nq.ma_nguoi_dung=$USER_ID
          AND n.ten_dang_nhap='$USER_USER'
          AND n.email='$USER_EMAIL';" || cleanup_failed=1
    sql "DELETE FROM nguoi_dung
        WHERE ma_nguoi_dung=$USER_ID
          AND ten_dang_nhap='$USER_USER'
          AND email='$USER_EMAIL';" || cleanup_failed=1
    remaining="$(sql "SELECT COUNT(*) FROM nguoi_dung WHERE ma_nguoi_dung=$USER_ID;")" || cleanup_failed=1
    [[ "$remaining" = "0" ]] || cleanup_failed=1
  fi

  rm -rf "$TMP_DIR" || cleanup_failed=1
  if [[ "$cleanup_failed" -ne 0 ]]; then
    echo "[FAIL] Cleanup fixture run $RUN_ID khong hoan tat; khong xoa candidate thieu ownership proof" >&2
    [[ "$original_status" -ne 0 ]] || original_status=1
  else
    echo "[CLEANUP] Da xoa fixture exact-ID cua run $RUN_ID"
  fi
  exit "$original_status"
}

req() {
  local method="$1" path="$2" data="${3:-}" auth="${4:-}" content_type="${5:-application/json}"
  local args=(-sS --connect-timeout 10 --max-time 60 -o "$BODY_FILE" -w '%{http_code}' -X "$method" "$BASE$path")
  : > "$BODY_FILE"
  [[ -n "$data" ]] && args+=(-H "Content-Type: $content_type" --data-binary "$data")
  [[ -n "$auth" ]] && args+=(-H "Authorization: Bearer $auth")
  if ! CODE="$(curl "${args[@]}")"; then
    CODE="000"
    BODY="$(<"$BODY_FILE")"
    return 1
  fi
  BODY="$(<"$BODY_FILE")"
}

request_to_files() {
  local method="$1" path="$2" data="$3" auth="$4" body_file="$5" code_file="$6"
  local args=(-sS --connect-timeout 10 --max-time 60 -o "$body_file" -w '%{http_code}' -X "$method" "$BASE$path")
  : > "$body_file"
  : > "$code_file"
  [[ -n "$data" ]] && args+=(-H 'Content-Type: application/json' --data-binary "$data")
  [[ -n "$auth" ]] && args+=(-H "Authorization: Bearer $auth")
  curl "${args[@]}" > "$code_file"
}

json_int_from_text() {
  local key="$1" text="$2" value
  value="$({ printf '%s' "$text" | grep -oE "\"$key\":[0-9]+" | grep -oE '[0-9]+' | sed -n '1p'; } || true)"
  printf '%s' "$value"
}

json_int() {
  json_int_from_text "$1" "$BODY"
}

extract_jwt() {
  local text="$1" value
  value="$({ printf '%s' "$text" | grep -oE 'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' | sed -n '1p'; } || true)"
  printf '%s' "$value"
}

ok() {
  PASS=$((PASS + 1))
  echo "  [PASS] $1"
}

fail() {
  FAIL=$((FAIL + 1))
  echo "  [FAIL] $1" >&2
  return 1
}

expect_code() {
  local expected="$1" label="$2"
  [[ "$CODE" = "$expected" ]] && ok "$label ($CODE)" || fail "$label: HTTP $CODE, mong $expected; body=$BODY"
}

expect_auth_denied() {
  local label="$1"
  if [[ "$CODE" = "401" || "$CODE" = "403" ]]; then
    ok "$label ($CODE)"
  else
    fail "$label: HTTP $CODE, mong 401/403; body=$BODY"
  fi
}

expect_method_closed() {
  local label="$1"
  [[ "$CODE" = "405" ]] && ok "$label (405)" || fail "$label: HTTP $CODE, mong 405; body=$BODY"
}

expect_stack_blocked() {
  local label="$1"
  if [[ "$CODE" = "403" || "$CODE" = "404" || "$CODE" = "405" ]]; then
    ok "$label ($CODE)"
  else
    fail "$label: HTTP $CODE; body=$BODY"
  fi
}

stock() {
  is_numeric_id "$BOOK_ID" || return 1
  sql "SELECT so_luong FROM sach WHERE ma_sach=$BOOK_ID;"
}

expect_stock() {
  local expected="$1" label="$2" actual
  actual="$(stock)"
  [[ "$actual" = "$expected" ]] && ok "$label ($actual)" || fail "$label: ton=$actual, mong $expected"
}

trap 'cleanup $?' EXIT
trap 'cleanup 130' INT
trap 'cleanup 143' TERM

echo "== Kiem tra ton kho delta @ $BASE (run $RUN_ID) =="

# Readiness retry co gioi han de khong race backend restart tu smoke-setup.sh.
for _ in $(seq 1 30); do
  if req GET "/api/sach?page=0" && [[ "$CODE" = "200" ]]; then
    break
  fi
  sleep 2
done
expect_code 200 "Backend san sang"

req POST "/tai-khoan/dang-nhap" "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}"
JWT="$(extract_jwt "$BODY")"
[[ "$CODE" = "200" && -n "$JWT" ]] || fail "Khong login duoc admin; kiem tra credential smoke local"
ok "Login admin thanh cong"

ADMIN_ID="$(discover_unique_id \
    "SELECT COUNT(*) FROM nguoi_dung WHERE ten_dang_nhap='$ADMIN_USER';" \
    "SELECT ma_nguoi_dung FROM nguoi_dung WHERE ten_dang_nhap='$ADMIN_USER';")" \
    || fail "Principal admin khong ton tai hoac khong unique trong DB"
is_numeric_id "$ADMIN_ID" || fail "Khong xac dinh duoc principal admin trong DB"
USER_ROLE_ID="$(discover_unique_id \
    "SELECT COUNT(*) FROM quyen WHERE ten_quyen='USER';" \
    "SELECT ma_quyen FROM quyen WHERE ten_quyen='USER';")" \
    || fail "Quyen USER khong ton tai hoac khong unique trong DB"

# Tao non-admin fixture rieng bang hash credential admin da duoc login-chung-minh;
# khong sua password/role cua bat ky shared user nao.
USER_CREATE_ATTEMPTED=1
sql "INSERT INTO nguoi_dung
    (ho_dem, ten, ten_dang_nhap, mat_khau, gioi_tinh, email, da_kich_hoat)
    SELECT 'Stock', 'Delta', '$USER_USER', mat_khau, 'N', '$USER_EMAIL', 1
    FROM nguoi_dung
    WHERE ma_nguoi_dung=$ADMIN_ID
      AND ten_dang_nhap='$ADMIN_USER';"
USER_ID="$(discover_unique_id \
    "SELECT COUNT(*) FROM nguoi_dung WHERE ten_dang_nhap='$USER_USER' AND email='$USER_EMAIL';" \
    "SELECT ma_nguoi_dung FROM nguoi_dung WHERE ten_dang_nhap='$USER_USER' AND email='$USER_EMAIL';")" \
    || fail "Khong tao duoc non-admin fixture unique"
sql "INSERT INTO nguoidung_quyen (ma_nguoi_dung, ma_quyen)
    VALUES ($USER_ID, $USER_ROLE_ID);"

req POST "/tai-khoan/dang-nhap" "{\"username\":\"$USER_USER\",\"password\":\"$USER_PASS\"}"
USER_JWT="$(extract_jwt "$BODY")"
[[ "$CODE" = "200" && -n "$USER_JWT" ]] \
  || fail "Khong login duoc non-admin fixture: HTTP $CODE; body=$BODY"
ok "Login non-admin fixture thanh cong"

CREATE_BODY="{\"tenSach\":\"$BOOK_TITLE\",\"tenTacGia\":\"Stock delta runtime\",\"slug\":\"$BOOK_SLUG\",\"moTaChiTiet\":\"Dedicated runtime fixture\",\"giaNiemYet\":20000,\"giaBan\":10000,\"soLuongTon\":10,\"isActive\":1,\"maTheLoaiList\":[],\"listImageStr\":[]}"
BOOK_CREATE_ATTEMPTED=1
req POST "/api/admin/sach/insert" "$CREATE_BODY" "$JWT"
expect_code 200 "Tao sach fixture voi ton ban dau"
BOOK_ID="$(json_int maSach)"
if ! is_numeric_id "$BOOK_ID"; then
  BOOK_ID="$(discover_unique_id \
      "SELECT COUNT(*) FROM sach WHERE slug='$BOOK_SLUG';" \
      "SELECT ma_sach FROM sach WHERE slug='$BOOK_SLUG';")" || fail "Khong capture duoc maSach fixture unique"
fi
expect_stock 10 "Create giu soLuongTon ban dau"

ADDRESS_BODY="{\"hoTen\":\"Stock Delta Runtime\",\"soDienThoai\":\"0900000000\",\"diaChiDayDu\":\"$ADDRESS_TEXT\",\"macDinh\":false}"
ADDRESS_CREATE_ATTEMPTED=1
req POST "/api/dia-chi" "$ADDRESS_BODY" "$JWT"
expect_code 200 "Tao dia chi fixture"
ADDRESS_ID="$(json_int maDiaChi)"
if ! is_numeric_id "$ADDRESS_ID"; then
  ADDRESS_ID="$(discover_unique_id \
      "SELECT COUNT(*) FROM dia_chi_giao_hang WHERE dia_chi_day_du='$ADDRESS_TEXT' AND ma_nguoi_dung=$ADMIN_ID;" \
      "SELECT ma_dia_chi FROM dia_chi_giao_hang WHERE dia_chi_day_du='$ADDRESS_TEXT' AND ma_nguoi_dung=$ADMIN_ID;")" \
      || fail "Khong capture duoc maDiaChi fixture unique"
fi

# Contract PATCH va bounds.
req PATCH "/api/admin/sach/$BOOK_ID/ton-kho" '{"soLuongThayDoi":5}' "$JWT"
expect_code 200 "PATCH +5"
[[ "$BODY" == *"\"maSach\":$BOOK_ID"* && "$BODY" == *'"soLuongTon":15'* ]] \
  && ok "Response +5 co maSach va ton authoritative" || fail "Response +5 sai: $BODY"
expect_stock 15 "DB sau +5"

req PATCH "/api/admin/sach/$BOOK_ID/ton-kho" '{"soLuongThayDoi":-5}' "$JWT"
expect_code 200 "PATCH -5"
expect_stock 10 "DB sau -5"

req PATCH "/api/admin/sach/$BOOK_ID/ton-kho" '{"soLuongThayDoi":-11}' "$JWT"
expect_code 409 "Delta lam ton am bi conflict"
expect_stock 10 "Conflict khong doi DB"

for invalid_body in '{"soLuongThayDoi":0}' '{"soLuongThayDoi":1.5}' '{not-json'; do
  req PATCH "/api/admin/sach/$BOOK_ID/ton-kho" "$invalid_body" "$JWT"
  expect_code 400 "Body delta khong hop le bi tu choi"
  [[ "$BODY" == *'"error"'* ]] || fail "Body 400 khong co error: $BODY"
done
expect_stock 10 "Body sai khong doi DB"

[[ "$(sql "SELECT COUNT(*) FROM sach WHERE ma_sach=2147483647;")" = "0" ]] \
  || fail "Khong co ID sach missing an toan de test 404"
req PATCH "/api/admin/sach/2147483647/ton-kho" '{"soLuongThayDoi":1}' "$JWT"
expect_code 404 "Sach khong ton tai"

req PATCH "/api/admin/sach/$BOOK_ID/ton-kho" '{"soLuongThayDoi":1}' ""
expect_auth_denied "Anonymous khong dieu chinh ton"
req PATCH "/api/admin/sach/$BOOK_ID/ton-kho" '{"soLuongThayDoi":1}' "$USER_JWT"
expect_auth_denied "Non-admin khong dieu chinh ton"
expect_stock 10 "Caller khong du quyen khong doi DB"

# CORS preflight mo phong dung browser PATCH co Authorization + JSON.
: > "$HEADER_FILE"
CODE="$(curl -sS --connect-timeout 10 --max-time 60 -o "$BODY_FILE" -D "$HEADER_FILE" -w '%{http_code}' -X OPTIONS \
  "$BASE/api/admin/sach/$BOOK_ID/ton-kho" \
  -H 'Origin: http://localhost:3000' \
  -H 'Access-Control-Request-Method: PATCH' \
  -H 'Access-Control-Request-Headers: authorization, content-type')"
[[ "$CODE" = "200" ]] || fail "PATCH preflight HTTP $CODE"
grep -qiE '^Access-Control-Allow-Origin:[[:space:]]*http://localhost:3000' "$HEADER_FILE" \
  || fail "CORS preflight sai allowed origin"
grep -qiE '^Access-Control-Allow-Methods:.*PATCH' "$HEADER_FILE" \
  || fail "CORS preflight thieu PATCH"
grep -qiE '^Access-Control-Allow-Headers:.*authorization' "$HEADER_FILE" \
  || fail "CORS preflight thieu Authorization"
grep -qiE '^Access-Control-Allow-Headers:.*content-type' "$HEADER_FILE" \
  || fail "CORS preflight thieu Content-Type"
ok "CORS preflight cho origin, PATCH va request headers"

# Raw route khong ton tai; Data REST PUT/DELETE voi ADMIN phai den method exposure va tra 405.
req PUT "/api/sach/update/$BOOK_ID" '{"soLuong":999}' "$JWT"
expect_stack_blocked "Raw /api/sach/update khong la bypass"

req POST "/sach" '{"tenSach":"Raw create","soLuong":999}' "$JWT"
expect_stack_blocked "Data REST collection POST bi security/REST exposure chan"
req PUT "/sach/$BOOK_ID" '{"tenSach":"Raw mutation","soLuong":999}' "$JWT"
expect_method_closed "Data REST item PUT exposure bi tat"
req PATCH "/sach/$BOOK_ID" '{"soLuong":999}' "$JWT"
expect_stack_blocked "Data REST item PATCH bi security/REST exposure chan"
req DELETE "/sach/$BOOK_ID" "" "$JWT"
expect_method_closed "Data REST item DELETE exposure bi tat"
req DELETE "/sach" "" "$JWT"
expect_stack_blocked "Data REST collection DELETE bi security/REST exposure chan"
req PUT "/sach/$BOOK_ID/listTheLoai" "/theLoais/1" "$JWT" "text/uri-list"
expect_method_closed "Data REST association PUT exposure bi tat"
req DELETE "/sach/$BOOK_ID/listTheLoai" "" "$JWT"
expect_method_closed "Data REST association DELETE exposure bi tat"
req GET "/sach/$BOOK_ID/listDanhGia"
expect_code 200 "Data REST relation GET van hoat dong"
[[ "$(sql "SELECT COUNT(*) FROM sach_theloai WHERE ma_sach=$BOOK_ID;")" = "0" ]] \
  || fail "Association bi thay doi qua route bi chan"
expect_stock 10 "Bypass routes khong doi DB"

# Mandatory flow: 10 -> checkout -2 -> stale metadata PUT(10) -> admin +5 -> 13.
CHECKOUT_BODY="{\"items\":[{\"maSach\":$BOOK_ID,\"soLuong\":2}],\"maDiaChiGiaoHang\":$ADDRESS_ID,\"phuongThucThanhToan\":\"COD\"}"
CHECKOUT_ATTEMPTS=$((CHECKOUT_ATTEMPTS + 1))
req POST "/api/don-hang/them" "$CHECKOUT_BODY" "$JWT"
expect_code 200 "Checkout fixture -2"
ORDER_ID="$(json_int maDonHang)"
is_numeric_id "$ORDER_ID" || fail "Khong capture duoc maDonHang checkout"
append_order_id "$ORDER_ID"
expect_stock 8 "Checkout 10 -> 8"

STALE_METADATA="{\"tenSach\":\"$BOOK_TITLE\",\"tenTacGia\":\"Metadata after checkout\",\"slug\":\"$BOOK_SLUG\",\"moTaChiTiet\":\"Stale stock must be ignored\",\"giaNiemYet\":20000,\"giaBan\":10000,\"soLuongTon\":10,\"isActive\":1,\"maTheLoaiList\":[],\"listImageStr\":[]}"
req PUT "/api/admin/sach/update/$BOOK_ID" "$STALE_METADATA" "$JWT"
expect_code 200 "Metadata PUT legacy"
expect_stock 8 "Metadata PUT stale khong restore ton 10"

req PATCH "/api/admin/sach/$BOOK_ID/ton-kho" '{"soLuongThayDoi":5}' "$JWT"
expect_code 200 "Admin +5 sau checkout/metadata"
[[ "$BODY" == *'"soLuongTon":13'* ]] || fail "Response cuoi khong co soLuongTon=13: $BODY"
expect_stock 13 "Mandatory flow ket thuc dung 13"

# Contention smoke 1: checkout -2 va admin +5 cung bat dau gan nhau; khong phai deterministic race proof.
req PATCH "/api/admin/sach/$BOOK_ID/ton-kho" '{"soLuongThayDoi":-3}' "$JWT"
expect_code 200 "Reset fixture 13 -> 10 bang delta"
expect_stock 10 "Ton truoc checkout/admin contention"

CHECKOUT_ATTEMPTS=$((CHECKOUT_ATTEMPTS + 1))
request_to_files POST "/api/don-hang/them" "$CHECKOUT_BODY" "$JWT" \
  "$TMP_DIR/checkout-admin-body" "$TMP_DIR/checkout-admin-code" &
PID_CHECKOUT=$!
request_to_files PATCH "/api/admin/sach/$BOOK_ID/ton-kho" '{"soLuongThayDoi":5}' "$JWT" \
  "$TMP_DIR/admin-checkout-body" "$TMP_DIR/admin-checkout-code" &
PID_DELTA=$!
set +e
wait "$PID_CHECKOUT"; WAIT_CHECKOUT=$?
PID_CHECKOUT=""
wait "$PID_DELTA"; WAIT_DELTA=$?
PID_DELTA=""
set -e
[[ "$WAIT_CHECKOUT" -eq 0 && "$WAIT_DELTA" -eq 0 ]] \
  || fail "curl checkout/admin contention that bai ($WAIT_CHECKOUT/$WAIT_DELTA)"
CODE_CHECKOUT="$(<"$TMP_DIR/checkout-admin-code")"
CODE_DELTA="$(<"$TMP_DIR/admin-checkout-code")"
[[ "$CODE_CHECKOUT" = "200" && "$CODE_DELTA" = "200" ]] \
  || fail "checkout/admin contention HTTP $CODE_CHECKOUT/$CODE_DELTA"
ORDER_ID="$(json_int_from_text maDonHang "$(<"$TMP_DIR/checkout-admin-body")")"
is_numeric_id "$ORDER_ID" || fail "Khong capture order contention checkout/admin"
append_order_id "$ORDER_ID"
expect_stock 13 "Checkout -2 + admin +5 contention cho tong 13"

# Contention smoke 2: cancel +2 va admin -1 tren cung fixture cho tong 9.
req PATCH "/api/admin/sach/$BOOK_ID/ton-kho" '{"soLuongThayDoi":-3}' "$JWT"
expect_code 200 "Reset fixture 13 -> 10 cho cancel/admin"
CHECKOUT_ATTEMPTS=$((CHECKOUT_ATTEMPTS + 1))
req POST "/api/don-hang/them" "$CHECKOUT_BODY" "$JWT"
expect_code 200 "Tao order cho cancel/admin contention"
CANCEL_ORDER_ID="$(json_int maDonHang)"
is_numeric_id "$CANCEL_ORDER_ID" || fail "Khong capture order cancel/admin"
append_order_id "$CANCEL_ORDER_ID"
expect_stock 8 "Checkout truoc cancel/admin 10 -> 8"

request_to_files POST "/api/don-hang/huy/$CANCEL_ORDER_ID" "" "$JWT" \
  "$TMP_DIR/cancel-admin-body" "$TMP_DIR/cancel-admin-code" &
PID_CANCEL=$!
request_to_files PATCH "/api/admin/sach/$BOOK_ID/ton-kho" '{"soLuongThayDoi":-1}' "$JWT" \
  "$TMP_DIR/admin-cancel-body" "$TMP_DIR/admin-cancel-code" &
PID_DELTA=$!
set +e
wait "$PID_CANCEL"; WAIT_CANCEL=$?
PID_CANCEL=""
wait "$PID_DELTA"; WAIT_DELTA=$?
PID_DELTA=""
set -e
[[ "$WAIT_CANCEL" -eq 0 && "$WAIT_DELTA" -eq 0 ]] \
  || fail "curl cancel/admin contention that bai ($WAIT_CANCEL/$WAIT_DELTA)"
CODE_CANCEL="$(<"$TMP_DIR/cancel-admin-code")"
CODE_DELTA="$(<"$TMP_DIR/admin-cancel-code")"
[[ "$CODE_CANCEL" = "200" && "$CODE_DELTA" = "200" ]] \
  || fail "cancel/admin contention HTTP $CODE_CANCEL/$CODE_DELTA"
expect_stock 9 "Cancel +2 + admin -1 contention cho tong 9"

# Script nay chi la HTTP/contention smoke; deterministic concurrency proof nam trong *IT co latch/timeout.
echo "== Ket qua: PASS=$PASS FAIL=$FAIL =="
[[ "$FAIL" -eq 0 ]]
