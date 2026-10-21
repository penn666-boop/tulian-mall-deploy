#!/usr/bin/env python3
"""
途链茶集 —— 把商城接口挂进服务器上已有的 HTTPS 站点（不新增端口）。

会在监听 443 的 server 块里插入：
    location /wx/      -> 127.0.0.1:8080
    location /admin/   -> 127.0.0.1:8080
    location /storage/ -> 127.0.0.1:8080
已存在同名 location 则跳过；修改前自动备份。
"""
import re
import shutil
import subprocess
import sys
from datetime import datetime

SNIPPET = """
    # ===== 途链茶集商城（自动注入） =====
    location /wx/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }
    location /admin/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    location /storage/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
    }
    # ===== 途链茶集商城 结束 =====
"""


def nginx_dump() -> str:
    out = subprocess.run(["nginx", "-T"], capture_output=True, text=True)
    return out.stdout or ""


def find_target_file() -> str | None:
    """返回包含 'listen ... 443' 的配置文件路径"""
    cur = None
    for line in nginx_dump().splitlines():
        m = re.match(r"# configuration file (.+):$", line.strip())
        if m:
            cur = m.group(1)
            continue
        if re.search(r"\blisten\b[^;]*\b443\b", line) and cur and cur != "/etc/nginx/nginx.conf":
            return cur
    return None


def main() -> int:
    target = find_target_file()
    if not target:
        print("未找到监听 443 的站点配置；请用 PUBLIC_PORT=8088 ./server-init.sh 改用独立端口", file=sys.stderr)
        return 1

    src = open(target, encoding="utf-8").read()
    if "途链茶集商城（自动注入）" in src or re.search(r"location\s+/wx/\s*\{", src):
        print(f"{target} 已包含商城配置，跳过")
        return 0

    # 在 443 的 listen 行之后插入（仍在 server 块内）
    lines = src.split("\n")
    idx = None
    for i, line in enumerate(lines):
        if re.search(r"\blisten\b[^;]*\b443\b", line):
            idx = i
            break
    if idx is None:
        print("配置里没有 443 listen 行，跳过", file=sys.stderr)
        return 1

    shutil.copyfile(target, f"{target}.bak.{datetime.now():%Y%m%d%H%M%S}")
    lines.insert(idx + 1, SNIPPET)
    open(target, "w", encoding="utf-8").write("\n".join(lines))
    print(f"已注入商城 location 到 {target}（原文件已备份 .bak）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
