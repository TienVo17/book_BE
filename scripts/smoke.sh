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

echo "== Ket qua: PASS=$PASS FAIL=$FAIL =="
[ "$FAIL" -eq 0 ]
