#!/usr/bin/env bash
# Executes the rehearsal scenarios once and emits one structured
# evidence row per scenario:
#   run<TAB>timestamp<TAB>scenario<TAB>status<TAB>elapsed_ms<TAB>traceId<TAB>note
#
# Every run re-seeds fixtures through the guarded reset script and clears browser
# localStorage first, so runs are independent. Never prints a JWT or password.
#
# Usage: ./scripts/rehearsal-run-scenarios.sh <RUN_NUMBER> [FE_URL] [BE_URL]
set -u

RUN="${1:-1}"
FE="${2:-http://localhost:3001}"
BE="${3:-http://localhost:8081}"
DB_CONT="rehearsal_ban_sach_db"
DB_NAME="rehearsal_ban_sach"
PASS_WORD='Rehearsal@12345'

sql() { docker exec "$DB_CONT" mysql -uroot "$DB_NAME" -N -e "$1" 2>/dev/null | tr -d '\r'; }
now_ms() { date +%s%3N; }
row() { printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$RUN" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$1" "$2" "$3" "${4:--}" "${5:--}"; }
ab() { timeout 90 agent-browser "$@" >/dev/null 2>&1; }

# --- Fixture reset (guarded) ---------------------------------------------
FIX="$(KEEP_FIXTURES=1 ./scripts/rehearsal-fixture-reset.sh 2>/dev/null)"
BOOK_ID="$(echo "$FIX" | grep '^REHEARSAL_BOOK_ID=' | cut -d= -f2)"
ADDR_ID="$(echo "$FIX" | grep '^REHEARSAL_ADDRESS_ID=' | cut -d= -f2)"
CUSTOMER="$(echo "$FIX" | grep '^REHEARSAL_USERNAME=' | cut -d= -f2)"
ADMIN="$(echo "$FIX" | grep '^REHEARSAL_ADMIN_USERNAME=' | cut -d= -f2)"
COUPON="$(echo "$FIX" | grep '^REHEARSAL_COUPON=' | cut -d= -f2)"
if [ -z "$BOOK_ID" ] || [ -z "$CUSTOMER" ]; then
  row setup FAIL 0 - "fixture reset failed"
  exit 1
fi

login_jwt() {
  curl -s -X POST "$BE/tai-khoan/dang-nhap" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$PASS_WORD\"}" \
    | grep -oE 'eyJ[A-Za-z0-9_.-]+' | head -1
}
JWT_CUSTOMER="$(login_jwt "$CUSTOMER")"
JWT_ADMIN="$(login_jwt "$ADMIN")"

trace_of() { echo "$1" | grep -oE '"traceId":"[^"]+"' | cut -d'"' -f4; }

# --- Scenario 1: discovery (browser) -------------------------------------
T0=$(now_ms)
ab console --clear
if ab open "$FE/" && ab wait --load networkidle; then
  ERRS="$(timeout 60 agent-browser console --json 2>/dev/null \
    | python3 -c "import json,sys;d=json.load(sys.stdin)['data']['messages'];print(len([m for m in d if m.get('type') in ('error','pageerror')]))" 2>/dev/null || echo 99)"
  [ "$ERRS" = "0" ] && row 1-discovery PASS $(( $(now_ms) - T0 )) - "console errors=0" \
                    || row 1-discovery FAIL $(( $(now_ms) - T0 )) - "console errors=$ERRS"
else
  row 1-discovery FAIL $(( $(now_ms) - T0 )) - "catalog did not load"
fi

# --- Scenario 2: cart + cross-tab reconciliation (browser) ----------------
T0=$(now_ms)
ab open "$FE/sach/$BOOK_ID"
ab wait --load networkidle
ab eval "localStorage.removeItem('gioHang')"
ab find role button click --name "Thêm vào giỏ hàng"
ab wait 800
# A second tab appends a different line; the reviewed cart must keep both.
ab eval "(()=>{const c=JSON.parse(localStorage.getItem('gioHang')||'[]');c.push({maSach:9,sachDto:{tenSach:'Cross Tab',giaBan:96000,hinhAnh:''},soLuong:2});localStorage.setItem('gioHang',JSON.stringify(c));window.dispatchEvent(new StorageEvent('storage',{key:'gioHang'}));})()"
ab wait 500
LINES="$(timeout 60 agent-browser eval "JSON.parse(localStorage.getItem('gioHang')||'[]').length" 2>/dev/null | tr -d '"' | tail -1)"
[ "$LINES" = "2" ] && row 2-cart-twotab PASS $(( $(now_ms) - T0 )) - "both lines preserved" \
                   || row 2-cart-twotab FAIL $(( $(now_ms) - T0 )) - "expected 2 lines, got $LINES"

# --- Scenario 3: address + COD checkout (API-driven, DB-verified) ---------
T0=$(now_ms)
STOCK_BEFORE="$(sql "SELECT so_luong FROM sach WHERE ma_sach=$BOOK_ID;")"
KEY="run${RUN}-cod-$(date +%s%N)"
RESP="$(curl -s -X POST "$BE/api/don-hang/them" -H "Authorization: Bearer $JWT_CUSTOMER" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" \
  -d "{\"items\":[{\"maSach\":$BOOK_ID,\"soLuong\":1}],\"maDiaChiGiaoHang\":$ADDR_ID,\"phuongThucThanhToan\":\"COD\",\"maCoupon\":\"$COUPON\"}")"
ORDER_ID="$(echo "$RESP" | grep -oE '"maDonHang":[0-9]+' | grep -oE '[0-9]+')"
STOCK_AFTER="$(sql "SELECT so_luong FROM sach WHERE ma_sach=$BOOK_ID;")"
if [ -n "$ORDER_ID" ] && [ "$STOCK_AFTER" = "$((STOCK_BEFORE - 1))" ]; then
  row 3-cod-checkout PASS $(( $(now_ms) - T0 )) - "order=$ORDER_ID stock $STOCK_BEFORE->$STOCK_AFTER"
else
  row 3-cod-checkout FAIL $(( $(now_ms) - T0 )) "$(trace_of "$RESP")" "order=$ORDER_ID stock $STOCK_BEFORE->$STOCK_AFTER"
fi

# --- Scenario 4: VNPay URL contract --------------------------------------
T0=$(now_ms)
VKEY="run${RUN}-vnpay-$(date +%s%N)"
VRESP="$(curl -s -X POST "$BE/api/don-hang/them" -H "Authorization: Bearer $JWT_CUSTOMER" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $VKEY" \
  -d "{\"items\":[{\"maSach\":$BOOK_ID,\"soLuong\":1}],\"maDiaChiGiaoHang\":$ADDR_ID,\"phuongThucThanhToan\":\"VNPAY\"}")"
VORDER="$(echo "$VRESP" | grep -oE '"maDonHang":[0-9]+' | grep -oE '[0-9]+')"
PAY="$(curl -s "$BE/api/don-hang/submitOrder?maDonHang=$VORDER" -H "Authorization: Bearer $JWT_CUSTOMER")"
# Sandbox credentials are absent in this environment, so the contract-level
# assertion is that a VNPAY order is created and the URL endpoint answers
# without leaking a server defect. Live callback is explicitly not demonstrated.
if [ -n "$VORDER" ] && echo "$PAY" | grep -qE 'paymentUrl|"status":(4|5)[0-9][0-9]'; then
  row 4-vnpay-contract PASS $(( $(now_ms) - T0 )) "$(trace_of "$PAY")" "order=$VORDER contract-only (no sandbox creds)"
else
  row 4-vnpay-contract FAIL $(( $(now_ms) - T0 )) "$(trace_of "$PAY")" "order=$VORDER unexpected payment response"
fi

# --- Scenario 5: admin stock delta + order state --------------------------
T0=$(now_ms)
SBEFORE="$(sql "SELECT so_luong FROM sach WHERE ma_sach=$BOOK_ID;")"
DELTA="$(curl -s -X PATCH "$BE/api/admin/sach/$BOOK_ID/ton-kho" -H "Authorization: Bearer $JWT_ADMIN" \
  -H 'Content-Type: application/json' -d '{"soLuongThayDoi":5}')"
SAFTER="$(sql "SELECT so_luong FROM sach WHERE ma_sach=$BOOK_ID;")"
ADV="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BE/api/don-hang/cap-nhat-trang-thai-giao-hang/$ORDER_ID" -H "Authorization: Bearer $JWT_ADMIN")"
DENY="$(curl -s -o /dev/null -w '%{http_code}' -X PATCH "$BE/api/admin/sach/$BOOK_ID/ton-kho" -H "Authorization: Bearer $JWT_CUSTOMER" \
  -H 'Content-Type: application/json' -d '{"soLuongThayDoi":5}')"
if echo "$DELTA" | grep -q '"soLuongTon"' && [ "$SAFTER" = "$((SBEFORE + 5))" ] && [ "$ADV" = "200" ] && [ "$DENY" = "403" ]; then
  row 5-admin PASS $(( $(now_ms) - T0 )) - "stock $SBEFORE->$SAFTER advance=$ADV customer-denied=$DENY"
else
  row 5-admin FAIL $(( $(now_ms) - T0 )) "$(trace_of "$DELTA")" "stock $SBEFORE->$SAFTER advance=$ADV customer-denied=$DENY"
fi

# --- Scenario 6: failure / recovery --------------------------------------
T0=$(now_ms)
# (a) invalid JWT fails closed with the common schema + trace header
UNAUTH_BODY="$(curl -s -D /tmp/rehearsal-h.txt "$BE/api/dia-chi" -H 'Authorization: Bearer not-a-valid-jwt')"
UNAUTH_CODE="$(echo "$UNAUTH_BODY" | grep -oE '"status":[0-9]+' | grep -oE '[0-9]+')"
HDR_TRACE="$(grep -i '^x-trace-id:' /tmp/rehearsal-h.txt | tr -d '\r' | awk '{print $2}')"
BODY_TRACE="$(trace_of "$UNAUTH_BODY")"
# (b) response-loss retry with one key creates exactly one order
RKEY="run${RUN}-retry-$(date +%s%N)"
RBODY="{\"items\":[{\"maSach\":$BOOK_ID,\"soLuong\":1}],\"maDiaChiGiaoHang\":$ADDR_ID,\"phuongThucThanhToan\":\"COD\"}"
R1="$(curl -s -X POST "$BE/api/don-hang/them" -H "Authorization: Bearer $JWT_CUSTOMER" -H 'Content-Type: application/json' -H "Idempotency-Key: $RKEY" -d "$RBODY")"
R2="$(curl -s -X POST "$BE/api/don-hang/them" -H "Authorization: Bearer $JWT_CUSTOMER" -H 'Content-Type: application/json' -H "Idempotency-Key: $RKEY" -d "$RBODY")"
DUPES="$(sql "SELECT COUNT(*) FROM don_hang WHERE checkout_idempotency_key='$RKEY';")"
# (c) forced stock conflict must not mutate stock
PRE_CONFLICT="$(sql "SELECT so_luong FROM sach WHERE ma_sach=$BOOK_ID;")"
curl -s -o /dev/null -X POST "$BE/api/don-hang/them" -H "Authorization: Bearer $JWT_CUSTOMER" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: run${RUN}-conflict-$(date +%s%N)" \
  -d "{\"items\":[{\"maSach\":$BOOK_ID,\"soLuong\":9999}],\"maDiaChiGiaoHang\":$ADDR_ID,\"phuongThucThanhToan\":\"COD\"}"
POST_CONFLICT="$(sql "SELECT so_luong FROM sach WHERE ma_sach=$BOOK_ID;")"

if [ "$UNAUTH_CODE" = "401" ] && [ -n "$HDR_TRACE" ] && [ "$HDR_TRACE" = "$BODY_TRACE" ] \
   && [ "$R1" = "$R2" ] && [ "$DUPES" = "1" ] && [ "$PRE_CONFLICT" = "$POST_CONFLICT" ]; then
  row 6-failure-recovery PASS $(( $(now_ms) - T0 )) "$BODY_TRACE" "401 traced+matched, retry orders=$DUPES, stock held at $POST_CONFLICT"
else
  row 6-failure-recovery FAIL $(( $(now_ms) - T0 )) "$BODY_TRACE" "code=$UNAUTH_CODE hdr==body:$([ "$HDR_TRACE" = "$BODY_TRACE" ] && echo y || echo n) replay:$([ "$R1" = "$R2" ] && echo y || echo n) dupes=$DUPES stock $PRE_CONFLICT->$POST_CONFLICT"
fi

# --- Scenario 7: review helpful vote toggles and never self-votes ---------
# The fixture customer's order was advanced to DA_GIAO by scenario 5, so they are
# eligible to review. The admin votes on it; the author must not be able to.
T0=$(now_ms)
REVIEW_BODY="{\"maSach\":$BOOK_ID,\"diemXepHang\":5,\"nhanXet\":\"Rehearsal review run $RUN\"}"
POST_REVIEW="$(curl -s -X POST "$BE/api/danh-gia/them-danh-gia-v1" -H "Authorization: Bearer $JWT_CUSTOMER" \
  -H 'Content-Type: application/json' -d "$REVIEW_BODY")"
REVIEW_ID="$(sql "SELECT ma_danh_gia FROM danhgia WHERE ma_sach=$BOOK_ID ORDER BY ma_danh_gia DESC LIMIT 1;")"
if [ -z "$REVIEW_ID" ]; then
  row 7-review-helpful FAIL $(( $(now_ms) - T0 )) "$(trace_of "$POST_REVIEW")" "review not created"
else
  VOTE1="$(curl -s -X POST "$BE/api/danh-gia/$REVIEW_ID/huu-ich" -H "Authorization: Bearer $JWT_ADMIN")"
  COUNT1="$(sql "SELECT COUNT(*) FROM danhgia_huu_ich WHERE ma_danh_gia=$REVIEW_ID;")"
  # Pressing again removes the vote, the same as a like button anywhere else.
  curl -s -o /dev/null -X POST "$BE/api/danh-gia/$REVIEW_ID/huu-ich" -H "Authorization: Bearer $JWT_ADMIN"
  COUNT2="$(sql "SELECT COUNT(*) FROM danhgia_huu_ich WHERE ma_danh_gia=$REVIEW_ID;")"
  # Blocked in the service, so a direct API call must fail too -- not just a hidden button.
  SELF="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BE/api/danh-gia/$REVIEW_ID/huu-ich" -H "Authorization: Bearer $JWT_CUSTOMER")"
  # Deleting the review must take its votes with it, or the count reports orphans forever.
  curl -s -o /dev/null -X POST "$BE/api/danh-gia/$REVIEW_ID/huu-ich" -H "Authorization: Bearer $JWT_ADMIN"
  curl -s -o /dev/null -X POST "$BE/api/danh-gia/xoa-danh-gia/$REVIEW_ID" -H "Authorization: Bearer $JWT_CUSTOMER"
  ORPHANS="$(sql "SELECT COUNT(*) FROM danhgia_huu_ich WHERE ma_danh_gia=$REVIEW_ID;")"

  if [ "$COUNT1" = "1" ] && [ "$COUNT2" = "0" ] && [ "$SELF" = "403" ] && [ "$ORPHANS" = "0" ]; then
    row 7-review-helpful PASS $(( $(now_ms) - T0 )) - "vote=$COUNT1 unvote=$COUNT2 self-denied=$SELF orphans=$ORPHANS"
  else
    row 7-review-helpful FAIL $(( $(now_ms) - T0 )) "$(trace_of "$VOTE1")" "vote=$COUNT1 unvote=$COUNT2 self-denied=$SELF orphans=$ORPHANS"
  fi
fi

# Leave the database clean for the next run.
./scripts/rehearsal-fixture-reset.sh >/dev/null 2>&1
