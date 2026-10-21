-- 途链茶集管理员初始化（自动生成，勿提交到公开仓库）
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO `litemall_role` (`id`,`name`,`desc`,`enabled`,`add_time`,`update_time`,`deleted`) VALUES
(1,'超级管理员','途链茶集系统最高权限',1,NOW(),NOW(),0),
(2,'商城管理员','负责商品、订单、会员日常运营',1,NOW(),NOW(),0),
(3,'推广管理员','负责优惠券、专题、团购',1,NOW(),NOW(),0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`desc`=VALUES(`desc`);

INSERT INTO `litemall_permission` (`role_id`,`permission`,`add_time`,`update_time`,`deleted`) VALUES
(1,'*',NOW(),NOW(),0),
(2,'GET /admin/goods/list',NOW(),NOW(),0),
(2,'POST /admin/goods/update',NOW(),NOW(),0),
(2,'POST /admin/goods/create',NOW(),NOW(),0),
(2,'GET /admin/order/list',NOW(),NOW(),0),
(2,'POST /admin/order/ship',NOW(),NOW(),0),
(2,'GET /admin/user/list',NOW(),NOW(),0),
(3,'GET /admin/coupon/list',NOW(),NOW(),0),
(3,'GET /admin/topic/list',NOW(),NOW(),0),
(3,'GET /admin/groupon/list',NOW(),NOW(),0);

INSERT INTO `litemall_admin` (`id`,`username`,`password`,`last_login_ip`,`last_login_time`,`avatar`,`add_time`,`update_time`,`deleted`,`role_ids`) VALUES
(1,'penn','$2a$10$gbQH/5gRUb1jORitkEY07Oxb5KCmoklHa0Jq7INlFSvRz0IHI2E1i','',NULL,'',NOW(),NOW(),0,'[1]')
ON DUPLICATE KEY UPDATE `password`=VALUES(`password`);

COMMIT;
