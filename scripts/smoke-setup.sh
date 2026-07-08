#!/usr/bin/env bash
# Cap credential admin da biet cho fixture smoke.
# Ly do: mat khau seed trong migration KHONG khop comment ("1") — gia tri that khong doan duoc.
# Script nay dat mat khau admin ve mot gia tri biet truoc de smoke login duoc (chi dung LOCAL/test).
set -eu
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-Smoke@12345}"
DB_CONTAINER="${DB_CONTAINER:-web_ban_sach_db}"
BE_CONTAINER="${BE_CONTAINER:-web_ban_sach_be}"
DB_NAME="${DB_NAME:-web_ban_sach}"

echo "== Sinh bcrypt hash cho '$ADMIN_USER' =="
HASH="$(docker run --rm -e P="$ADMIN_PASS" python:3-slim sh -c \
  "pip install bcrypt -q >/dev/null 2>&1 && python -c \"import bcrypt,os; print(bcrypt.hashpw(os.environ['P'].encode(), bcrypt.gensalt(rounds=10, prefix=b'2a')).decode())\"")"
echo "hash=$HASH"

echo "== UPDATE mat_khau trong DB ($DB_NAME.nguoi_dung) =="
docker exec "$DB_CONTAINER" mysql -uroot "$DB_NAME" \
  -e "UPDATE nguoi_dung SET mat_khau='$HASH' WHERE ten_dang_nhap='$ADMIN_USER';"

echo "== Restart backend de clear in-memory rate-limit =="
docker restart "$BE_CONTAINER" >/dev/null
echo "done ($ADMIN_USER / $ADMIN_PASS)."
