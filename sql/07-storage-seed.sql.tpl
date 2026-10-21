-- 途链茶集 · 商品占位图存储注册（幂等）
-- __BASE__ 由 deploy.sh 渲染为 https://<域名 或 IP:端口>
USE litemall;

INSERT INTO litemall_storage (`key`, name, type, size, url, add_time, deleted)
VALUES
__ROWS__
ON DUPLICATE KEY UPDATE url = VALUES(url), size = VALUES(size);

UPDATE litemall_goods g
JOIN litemall_storage s ON s.`key` = CONCAT('tulian-goods-', g.id, '.jpg')
SET g.pic_url = s.url;
