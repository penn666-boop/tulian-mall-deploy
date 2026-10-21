#!/usr/bin/env bash
# 途链茶集商城 —— 绑定正式域名（在服务器上执行，建议用 root）
#
# 用法：
#   DOMAIN=tl.shyxcy.com bash switch-domain.sh
#
# 做什么：
#   1. 校验 DNS 是否已解析到本机（避免白跑，Let's Encrypt 有签发频率限制）
#   2. 申请 / 复用 Let's Encrypt 证书
#   3. 新增一个独立 nginx server 块（不改动服务器上已有的站点配置）
#   4. 把数据库里商品图 / 广告图的地址从旧域名改成新域名
#   5. 更新 .env 里的回调地址与图片基址，重启商城容器
#   6. 自检并打印「微信后台需要配置的清单」
#
# 可重复执行（幂等）。

set -euo pipefail

DOMAIN="${DOMAIN:-tl.shyxcy.com}"
EMAIL="${EMAIL:-admin@shyxcy.com}"
APP_ROOT="${APP_ROOT:-/opt/tulian-mall}"
ADMIN_DIR="${ADMIN_DIR:-$APP_ROOT/admin}"
WEBROOT="/var/www/certbot"

if [ -z "$DOMAIN" ]; then
  echo "用法：DOMAIN=tl.shyxcy.com bash switch-domain.sh" >&2
  exit 1
fi

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$1"; }
warn() { printf '\033[1;33m%s\033[0m\n' "$1"; }
ok() { printf '\033[1;32m%s\033[0m\n' "$1"; }

[ "$(id -u)" = 0 ] || { echo "请用 root 运行（或 sudo）" >&2; exit 1; }

say "1/7 前置检查"
command -v docker >/dev/null || { echo "未检测到 docker" >&2; exit 1; }
command -v nginx  >/dev/null || { echo "未检测到 nginx" >&2; exit 1; }

if [ -d /etc/nginx/conf.d ]; then
  CONF_DIR=/etc/nginx/conf.d
elif [ -d /etc/nginx/sites-enabled ]; then
  CONF_DIR=/etc/nginx/sites-enabled
else
  echo "找不到 nginx 配置目录" >&2; exit 1
fi
echo "nginx 配置目录：$CONF_DIR"

# 本机公网 IP
PUB_IP="$(curl -fsS -m 8 https://api.ipify.org 2>/dev/null || curl -fsS -m 8 http://metadata.tencentyun.com/latest/meta-data/public-ipv4 2>/dev/null || true)"
echo "本机公网 IP：${PUB_IP:-未知}"

# 域名解析
RES_IP="$(getent hosts "$DOMAIN" 2>/dev/null | awk '{print $1}' | head -1 || true)"
if command -v dig >/dev/null; then
  RES_IP="$(dig +short "$DOMAIN" A 2>/dev/null | grep -E '^[0-9]+\.' | head -1 || true)"
fi
echo "$DOMAIN 解析到：${RES_IP:-无记录}"

if [ -z "$RES_IP" ]; then
  warn "域名还没有解析记录。请到你的域名解析控制台添加："
  warn "    记录类型 A    主机记录 ${DOMAIN%%.*}    记录值 ${PUB_IP:-<服务器公网IP>}"
  exit 1
fi
if [ -n "$PUB_IP" ] && [ "$RES_IP" != "$PUB_IP" ]; then
  warn "解析指向 ${RES_IP}，但本机公网 IP 是 ${PUB_IP}，二者不一致。"
  warn "请先修正解析再运行本脚本（否则证书签发会失败并消耗 Let's Encrypt 配额）。"
  exit 1
fi
ok "DNS 已生效：$DOMAIN -> $RES_IP"

say "2/7 探测容器与后台目录"
MYSQL_CT="$(docker ps --format '{{.Names}}' 2>/dev/null | grep -iE 'mysql' | head -1 || true)"
MALL_CT="$(docker ps --format '{{.Names}}' 2>/dev/null | grep -iE 'tulian-mall|^mall' | head -1 || true)"
[ -n "$MYSQL_CT" ] || warn "未找到 mysql 容器，数据库地址替换将跳过"
[ -n "$MALL_CT" ] || warn "未找到 mall 容器，稍后将尝试用 docker compose 启动"
echo "mysql 容器：${MYSQL_CT:-未找到}    商城容器：${MALL_CT:-未找到}"

# 自动探测管理后台静态站目录，避免路径猜错导致 /mall/ 404
for d in "$APP_ROOT/admin" /var/www/tulian-admin "$APP_ROOT/admin-dist" /var/www/tulian-mall-admin; do
  if [ -f "$d/index.html" ]; then ADMIN_DIR="$d"; break; fi
done
if [ ! -f "$ADMIN_DIR/index.html" ]; then
  warn "未找到后台静态站 index.html（将指向 ${ADMIN_DIR}，/mall/ 可能 404，但不影响小程序接口）"
fi
echo "后台静态站：$ADMIN_DIR"
if ! command -v certbot >/dev/null; then
  echo "安装 certbot..."
  apt-get update -qq && apt-get install -y -qq certbot
fi
mkdir -p "$WEBROOT"

say "3/7 申请 Let's Encrypt 证书"
ACME_CONF="$CONF_DIR/tulian-acme-tmp.conf"
cat > "$ACME_CONF" <<EOF
server {
    listen 80;
    server_name $DOMAIN;
    location /.well-known/acme-challenge/ { root $WEBROOT; }
    location / { return 503; }
}
EOF
nginx -t >/dev/null 2>&1 && systemctl reload nginx

if [ -f "/etc/letsencrypt/live/$DOMAIN/fullchain.pem" ]; then
  ok "证书已存在，跳过签发"
else
  # --expand：服务器上可能已有别的证书（如 IP 证书），新域名会合并进它而不新建目录
  certbot certonly --webroot -w "$WEBROOT" -d "$DOMAIN" \
    --expand --non-interactive --agree-tos --email "$EMAIL" --keep-until-expiring
fi
rm -f "$ACME_CONF"

# 探测包含该域名的证书实际所在目录（--expand 时可能与旧证书同目录）
CERT_DIR=""
for d in /etc/letsencrypt/live/*/; do
  [ -f "$d/fullchain.pem" ] || continue
  if openssl x509 -in "$d/fullchain.pem" -noout -ext subjectAltName 2>/dev/null | grep -q "$DOMAIN"; then
    CERT_DIR="$d"; break
  fi
done
if [ -z "$CERT_DIR" ]; then
  CERT_DIR="/etc/letsencrypt/live/$DOMAIN/"
  warn "未找到包含 $DOMAIN 的证书目录，按默认路径写入"
fi
echo "证书目录：$CERT_DIR"

# 自动续期
if command -v systemctl >/dev/null && systemctl list-unit-files 2>/dev/null | grep -q certbot.timer; then
  systemctl enable --now certbot.timer >/dev/null 2>&1 || true
fi
if ! crontab -l 2>/dev/null | grep -q "certbot renew"; then
  (crontab -l 2>/dev/null; echo "0 3 * * * certbot renew --quiet --deploy-hook 'systemctl reload nginx'") | crontab - 2>/dev/null || true
fi
ok "证书就绪：/etc/letsencrypt/live/$DOMAIN/fullchain.pem（已配置每日自动续期检查）"

say "4/7 写入 nginx 站点配置（独立文件，不动现有站点）"
SITE_CONF="$CONF_DIR/tulian-mall-domain.conf"
cp -f "$SITE_CONF" "$SITE_CONF.bak.$(date +%s)" 2>/dev/null || true

cat > "$SITE_CONF" <<EOF
server {
    listen 80;
    server_name $DOMAIN;
    return 301 https://\$host\$request_uri;
}

server {
    listen 443 ssl http2;
    server_name $DOMAIN;

    ssl_certificate     ${CERT_DIR}fullchain.pem;
    ssl_certificate_key ${CERT_DIR}privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;

    client_max_body_size 20m;

    # 小程序接口（request 合法域名走这里）
    location /wx/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 60s;
    }

    # 管理后台接口
    location /admin/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 60s;
    }

    # 图片等静态资源（后端 /wx/storage/fetch/ 已覆盖，此处兜底）
    location /storage/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host \$host;
    }

    # 管理后台静态站
    location = /mall { return 302 /mall/; }
    location /mall/ {
        alias $ADMIN_DIR/;
        index index.html;
        try_files \$uri \$uri/ /mall/index.html;
    }

    # 根路径进后台，方便直接访问
    location / {
        root $ADMIN_DIR;
        index index.html;
        try_files \$uri \$uri/ /index.html;
    }
}
EOF

nginx -t && systemctl reload nginx
ok "nginx 已加载 $SITE_CONF"

say "5/7 更新数据库中的图片地址"
ENV_FILE="$APP_ROOT/.env"
if [ ! -f "$ENV_FILE" ]; then
  warn "未找到 ${ENV_FILE}，跳过数据库与配置更新"
else
  OLD_BASE="$(grep -E '^TL_STORAGE_BASE_URL=' "$ENV_FILE" | cut -d= -f2- || true)"
  NEW_BASE="https://$DOMAIN"
  DB_PASS="$(grep -E '^MYSQL_PASSWORD=' "$ENV_FILE" | cut -d= -f2- || true)"

  query() {
    docker exec -i "$MYSQL_CT" sh -c "MYSQL_PWD=\"$DB_PASS\" exec mysql -ulitemall --default-character-set=utf8mb4 -N -B litemall"
  }

  if [ -z "$MYSQL_CT" ]; then
    warn "跳过数据库地址替换（未找到 mysql 容器）"
  elif [ -n "$OLD_BASE" ] && [ "$OLD_BASE" != "$NEW_BASE" ]; then
    echo "旧地址基址：$OLD_BASE"
    printf "UPDATE litemall_goods   SET pic_url = REPLACE(pic_url, '%s', '%s') WHERE pic_url LIKE '%%%s%%';\nUPDATE litemall_storage SET url      = REPLACE(url,      '%s', '%s') WHERE url      LIKE '%%%s%%';\nUPDATE litemall_ad      SET url      = REPLACE(url,      '%s', '%s') WHERE url      LIKE '%%%s%%';\nUPDATE litemall_brand   SET pic_url  = REPLACE(pic_url,  '%s', '%s') WHERE pic_url  LIKE '%%%s%%';\n" \
      "$OLD_BASE" "$NEW_BASE" "$OLD_BASE" \
      "$OLD_BASE" "$NEW_BASE" "$OLD_BASE" \
      "$OLD_BASE" "$NEW_BASE" "$OLD_BASE" \
      "$OLD_BASE" "$NEW_BASE" "$OLD_BASE" | query 2>/dev/null || warn "部分表更新失败（可能表不存在，可忽略）"
    ok "图片地址已改为 $NEW_BASE"
  else
    echo "无需变更（当前基址已是 ${OLD_BASE:-空}）"
  fi

  say "6/7 更新回调地址并重启商城"
  set_env() {
    local k="$1" v="$2"
    if grep -qE "^$k=" "$ENV_FILE"; then
      sed -i "s|^$k=.*|$k=$v|" "$ENV_FILE"
    else
      printf '%s=%s\n' "$k" "$v" >> "$ENV_FILE"
    fi
  }
  set_env TL_STORAGE_BASE_URL          "$NEW_BASE"
  set_env TL_WX_NOTIFY_URL             "$NEW_BASE/wx/order/pay-notify"
  set_env TL_WX_RECHARGE_NOTIFY_URL    "$NEW_BASE/wx/member/recharge-notify"
  grep -E '^TL_(STORAGE_BASE_URL|WX_NOTIFY_URL|WX_RECHARGE_NOTIFY_URL)=' "$ENV_FILE"

  (cd "$APP_ROOT" && docker compose up -d mall >/dev/null 2>&1 || docker-compose up -d mall >/dev/null 2>&1)
  for i in $(seq 1 40); do
    if curl -fsS -m 5 "http://127.0.0.1:8080/wx/home/index" >/dev/null 2>&1; then break; fi
    sleep 3
  done
  ok "商城容器已重启"
fi

say "7/7 自检"
sleep 2
for p in "/wx/home/index" "/wx/goods/list?page=1&limit=1" "/admin/auth/info" "/mall/"; do
  printf '  %-34s ' "$p"
  code="$(curl -sk -m 10 -o /dev/null -w '%{http_code}' "https://$DOMAIN$p" || echo ERR)"
  echo "$code"
done
PIC="$(curl -sk -m 10 "https://$DOMAIN/wx/goods/list?page=1&limit=1" | grep -o '"picUrl":"[^"]*"' | head -1 || true)"
echo "  商品图地址：$PIC"

echo
ok "================ 域名绑定完成 ================"
cat <<EOF

接下来请在微信侧完成 3 处配置（否则真机仍然连不上）：

【1】微信公众平台 → 开发管理 → 开发设置 → 服务器域名
     request 合法域名：   https://$DOMAIN
     uploadFile 合法域名：https://$DOMAIN
     downloadFile 域名：  https://$DOMAIN
     （一级域名默认通配二级，配 https://$DOMAIN 即可）

【2】微信小程序后台 → 管理 → 版本管理（提交审核前确认小程序备案已完成）

【3】微信支付商户平台 → 产品中心 → 开发配置 → 支付授权目录
     JSAPI 支付授权目录：https://$DOMAIN/

常用地址：
     商城接口    https://$DOMAIN/wx/home/index
     管理后台    https://$DOMAIN/mall/
     支付回调    https://$DOMAIN/wx/order/pay-notify
EOF
