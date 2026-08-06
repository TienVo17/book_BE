#!/usr/bin/env bash
# Cho phep frontend cua STACK REHEARSAL goi backend rehearsal (cong 8081).
#
# Vi sao can: nginx.conf cua production khoa connect-src vao dung hai origin —
# http://localhost:8080 (stack dev) va URL Render. Stack rehearsal chay backend o
# 8081 nen trinh duyet chan MOI request API bang CSP truoc khi no roi khoi trang:
# console bao "TypeError: Failed to fetch" va network tab khong ghi duoc request
# nao ca. curl van 200 vi curl khong ap dung CSP — do la ly do lech nay khong lo
# ra o cac buoc kiem tra bang API.
#
# Script chi va vao CONTAINER dang chay, khong sua nginx.conf trong repo: chinh
# sach cua production phai duoc giu nguyen van. Container rehearsal la disposable,
# nen thay doi nay bien mat cung no.
#
# GIOI HAN CUA BANG CHUNG: cac kich ban trinh duyet cua rehearsal chay duoi mot CSP
# khac production DUNG MOT muc connect-src. Moi chi thi khac (script-src, img-src,
# frame-ancestors, object-src...) giu nguyen.
#
# Usage: ./scripts/rehearsal-allow-frontend-csp.sh
set -u

# Git Bash tren Windows doi moi doi so hinh dang duong dan Unix thanh duong dan
# Windows, nen /etc/nginx/... ben trong container bi bien thanh C:/Program Files/...
# truoc khi docker nhan duoc. Hai bien nay tat viec do; tren Linux/macOS chung
# khong co tac dung phu nao.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

FE_CONT="rehearsal_ban_sach_fe"
FORBIDDEN_CONT="web_ban_sach_fe"
CSP_FILE="/etc/nginx/conf.d/default.conf"
ORIGIN_MOI="http://localhost:8081"

fail() { echo "  [ABORT] $1" >&2; exit 1; }

[ "$FE_CONT" = "$FORBIDDEN_CONT" ] \
  && fail "refusing to patch the development frontend container"

docker inspect "$FE_CONT" >/dev/null 2>&1 \
  || fail "container '$FE_CONT' not found; start the rehearsal stack first"

# Doi lai vo hai: chi them origin khi no chua co.
if docker exec "$FE_CONT" grep -q "$ORIGIN_MOI" "$CSP_FILE"; then
  echo "  [INFO] rehearsal origin already allowed"
  exit 0
fi

docker exec "$FE_CONT" sed -i \
  "s|connect-src 'self' http://localhost:8080|connect-src 'self' http://localhost:8080 $ORIGIN_MOI|" \
  "$CSP_FILE" \
  || fail "could not patch the CSP inside '$FE_CONT'"

docker exec "$FE_CONT" grep -q "$ORIGIN_MOI" "$CSP_FILE" \
  || fail "CSP patch did not apply; the directive text must have changed"

docker exec "$FE_CONT" nginx -s reload \
  || fail "nginx refused to reload after the CSP patch"

echo "  [INFO] rehearsal frontend now allows $ORIGIN_MOI in connect-src"
