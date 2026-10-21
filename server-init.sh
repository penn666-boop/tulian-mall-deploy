#!/usr/bin/env bash
# 途链茶集商城 —— 服务器端自举脚本（在服务器上执行，不需要从外部 SSH）
#
# 前置：当前目录已含部署包（litemall.jar / docker-compose.yml / sql / images / nginx），
#       且有 .env（含微信凭据与数据库口令）。
# 用法：
#   ./server-init.sh                      # 无域名：挂到现有 HTTPS 站点的 /wx/ /admin/ 路径下
#   DOMAIN=mall.example.com ./server-init.sh   # 有域名：独立 443 站点 + certbot
#   PUBLIC_PORT=8088 ./server-init.sh     # 无域名且不想动现有站点：单独占 8088
set -euo pipefail
cd "$(dirname "$0")"
DOMAIN="${DOMAIN:-}"
PUBLIC_PORT="${PUBLIC_PORT:-}"

say() { printf '\n\033[1;33m==> %s\033[0m\n' "$*"; }

# ---------- 1 环境 ----------
say "1/7 准备环境（Docker / rsync）"
export DEBIAN_FRONTEND=noninteractive
command -v rsync >/dev/null || (apt-get update -qq && apt-get install -y -qq rsync)
command -v python3 >/dev/null || apt-get install -y -qq python3
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sh -s -- --mirror Aliyun || curl -fsSL https://get.docker.com | sh
  systemctl enable --now docker
fi
command -v docker compose >/dev/null || apt-get install -y -qq docker-compose-plugin
mkdir -p /var/www/tulian-admin
[ -d admin-dist ] && cp -a admin-dist/. /var/www/tulian-admin/ && echo "已部署管理后台静态站"

# ---------- 2 基址 ----------
if [ -n "$DOMAIN" ]; then
  BASE="https://$DOMAIN"
elif [ -n "$PUBLIC_PORT" ]; then
  BASE="http://$(curl -s -m 3 ifconfig.me || echo 127.0.0.1):$PUBLIC_PORT"
else
  BASE="https://$(curl -s -m 3 ifconfig.me || echo 127.0.0.1)"
fi
say "2/7 对外基址 ${BASE}"
# 同步 .env 中的回调与图片地址
if [ -f .env ]; then
  sed -i \
    -e "s|^TL_STORAGE_BASE_URL=.*|TL_STORAGE_BASE_URL=$BASE|" \
    -e "s|^TL_WX_NOTIFY_URL=.*|TL_WX_NOTIFY_URL=$BASE/wx/order/pay-notify|" \
    -e "s|^TL_WX_RECHARGE_NOTIFY_URL=.*|TL_WX_RECHARGE_NOTIFY_URL=$BASE/wx/member/recharge-notify|" \
    .env
  chmod 600 .env
fi
DB_PASS=$(grep '^MYSQL_PASSWORD=' .env | cut -d= -f2-)
sed "s/__DB_PASSWORD__/${DB_PASS}/g" initdb/01-schema.sql.tpl > initdb/01-schema.sql
chmod 644 initdb/*.sql 2>/dev/null || true

# ---------- 3 构建镜像 ----------
say "3/7 构建运行镜像"
docker build -f Dockerfile.runtime -t tulian-tea-mall:latest .

# ---------- 4 起库并导入数据 ----------
say "4/7 启动数据库并导入数据"
docker compose up -d mysql
for i in $(seq 1 60); do
  if docker compose ps mysql | grep -q healthy; then break; fi
  sleep 3
done
mysql_exec() { docker exec -i tulian-mysql mysql -ulitemall -p"$DB_PASS" --default-character-set=utf8mb4 litemall; }

# 商品占位图：按当前基址渲染注册 SQL
if [ -f sql/07-storage-seed.sql.tpl ] && [ -d images ]; then
  python3 - <<PY
import glob, os
rows = []
for i, f in enumerate(sorted(glob.glob('images/*.jpg'))):
    gid = os.path.basename(f)[:-4]
    rows.append("('tulian-goods-%s.jpg','%s.jpg','image/jpeg',%d,'%s/wx/storage/fetch/tulian-goods-%s.jpg',NOW(),0)"
                % (gid, gid, os.path.getsize(f), "$BASE", gid))
tpl = open('sql/07-storage-seed.sql.tpl', encoding='utf-8').read()
open('sql/07-storage-seed.sql', 'w', encoding='utf-8').write(tpl.replace('__ROWS__', ',\n'.join(rows)))
print('已渲染 sql/07-storage-seed.sql（%d 张）' % len(rows))
PY
fi
for f in sql/04-member.sql sql/05-sku-import.sql sql/06-recharge-order.sql sql/07-storage-seed.sql; do
  [ -f "$f" ] || continue
  echo "执行 $f"
  mysql_exec < "$f" 2>/dev/null || true
done
echo "商品数: $(mysql_exec -N -B -e 'SELECT COUNT(*) FROM litemall_goods;' 2>/dev/null)"

# ---------- 5 起应用 ----------
say "5/7 启动应用"
docker compose up -d mall
if [ -d images ]; then
  docker exec tulian-mall mkdir -p /storage 2>/dev/null || true
  docker cp images/. tulian-mall:/storage/ 2>/dev/null || true
  echo "占位图: $(docker exec tulian-mall ls /storage/tulian-goods-*.jpg 2>/dev/null | wc -l) 张"
fi
for i in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/wx/home/index || echo 000)
  [ "$code" = "200" ] && break
  sleep 3
done
echo "本地 API: $code"

# ---------- 6 nginx ----------
say "6/7 配置 nginx"
if command -v nginx >/dev/null; then
  if [ -n "$DOMAIN" ]; then
    sed "s|__DOMAIN__|$DOMAIN|g" nginx/tulian-mall.conf > /etc/nginx/conf.d/tulian-mall.conf
    certbot --nginx -d "$DOMAIN" --non-interactive --agree-tos -m ops@tulian.local --keep-until-expiring 2>/dev/null \
      || echo "certbot 未成功，请手动申请证书"
  elif [ -n "$PUBLIC_PORT" ]; then
    sed -e "s|__SERVER__|_|g" -e "s|__PORT__|$PUBLIC_PORT|g" nginx/tulian-mall-port.conf > /etc/nginx/conf.d/tulian-mall.conf
  else
    # 无新端口：把商城挂进现有 HTTPS 站点（/wx/ /admin/ /storage/）
    python3 nginx/inject-locations.py
  fi
  nginx -t && systemctl reload nginx && echo "nginx 已重载"
else
  echo "未安装 nginx，请自行反代 127.0.0.1:8080"
fi

# ---------- 7 健康检查 ----------
say "7/7 健康检查"
sleep 3
curl -s -o /dev/null -w "  本地   %{http_code}\n" http://127.0.0.1:8080/wx/home/index
curl -sk -o /dev/null -w "  对外   %{http_code}\n" "$BASE/wx/home/index"
echo
echo "完成。商城基址: ${BASE}"
