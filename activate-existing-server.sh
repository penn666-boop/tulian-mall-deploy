#!/usr/bin/env bash
set -euo pipefail
cd /opt/tulian-mall
chmod 600 .env
# This is a new isolated database. Never import local transaction data.
docker compose up -d mysql
ready=0
for i in $(seq 1 60); do
 if [ "$(docker inspect -f '{{.State.Health.Status}}' tulian-mysql)" = healthy ]; then ready=1; break; fi
 sleep 3
done
[ "$ready" = 1 ]
query() { docker exec -i tulian-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -ulitemall --default-character-set=utf8mb4 -N -B litemall'; }
for f in sql/04-member.sql sql/06-recharge-order.sql; do query < "$f"; done
count=$(printf 'SELECT COUNT(*) FROM litemall_goods;' | query)
if [ "$count" = 0 ]; then query < sql/05-sku-import.sql; fi
query < sql/07-storage-seed.sql
docker compose up -d mall
docker cp images/. tulian-mall:/storage/
# Source image names are numeric; the API references prefixed object keys.
for f in images/*.jpg; do
 name=$(basename "$f")
 docker cp "$f" "tulian-mall:/storage/tulian-goods-$name"
done
ready=0
for i in $(seq 1 60); do
 if curl -fsS http://127.0.0.1:8080/wx/home/index > /tmp/tulian-home-check.json; then ready=1; break; fi
 sleep 3
done
[ "$ready" = 1 ]
python3 -c 'import json; assert json.load(open("/tmp/tulian-home-check.json"))["errno"] == 0'
printf 'SELECT COUNT(*) AS goods FROM litemall_goods; SELECT COUNT(*) AS users FROM litemall_user; SELECT COUNT(*) AS orders FROM litemall_order;' | query
