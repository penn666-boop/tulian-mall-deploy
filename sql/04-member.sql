-- 途链茶集 · 会员体系扩展表
-- 说明：litemall 原生只有 litemall_user.user_level(0/1/2)，没有等级规则、积分、储值。
-- 本文件补齐以下五张表，所有表以 tl_ 前缀区分于原生表。

SET NAMES utf8mb4;

-- 1. 会员等级规则（可后台配置，取代原生硬编码等级）
CREATE TABLE IF NOT EXISTS `tl_member_level` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(32) NOT NULL COMMENT '等级名称',
  `level` tinyint(3) NOT NULL COMMENT '等级值，写入 litemall_user.user_level',
  `min_growth` int(11) NOT NULL DEFAULT 0 COMMENT '达到该等级所需成长值',
  `discount` decimal(4,2) NOT NULL DEFAULT 1.00 COMMENT '折扣率，0.95 表示 95 折',
  `point_rate` decimal(4,2) NOT NULL DEFAULT 1.00 COMMENT '积分倍率',
  `desc` varchar(255) DEFAULT NULL COMMENT '等级权益说明',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `add_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_level` (`level`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COMMENT='会员等级规则表';

-- 2. 会员账户（每个用户一条，记录积分/储值/成长值）
CREATE TABLE IF NOT EXISTS `tl_member_account` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL COMMENT '对应 litemall_user.id',
  `points` int(11) NOT NULL DEFAULT 0 COMMENT '当前可用积分',
  `total_points` int(11) NOT NULL DEFAULT 0 COMMENT '累计获得积分（不含消费）',
  `balance` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '储值余额',
  `recharge_total` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '累计充值金额',
  `growth` int(11) NOT NULL DEFAULT 0 COMMENT '成长值',
  `add_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员账户表';

-- 3. 积分流水
CREATE TABLE IF NOT EXISTS `tl_points_log` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL COMMENT '会员ID',
  `type` tinyint(3) NOT NULL COMMENT '1 获得 2 消费 3 过期 4 退款退回 5 管理员调整',
  `amount` int(11) NOT NULL COMMENT '变动积分，正为获得，负为消耗',
  `balance` int(11) NOT NULL COMMENT '变动后可用积分',
  `source` varchar(32) NOT NULL DEFAULT '' COMMENT '来源：order/signup/recharge/refund/admin',
  `source_id` varchar(64) NOT NULL DEFAULT '' COMMENT '关联业务单号',
  `remark` varchar(255) DEFAULT NULL COMMENT '说明',
  `add_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `add_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水表';

-- 4. 储值余额流水
CREATE TABLE IF NOT EXISTS `tl_balance_log` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL COMMENT '会员ID',
  `type` tinyint(3) NOT NULL COMMENT '1 充值 2 消费抵扣 3 退款返还 4 管理员调整',
  `amount` decimal(10,2) NOT NULL COMMENT '变动金额，正为入账，负为出账',
  `balance` decimal(10,2) NOT NULL COMMENT '变动后余额',
  `source` varchar(32) NOT NULL DEFAULT '' COMMENT '来源：recharge/order/refund/admin',
  `source_id` varchar(64) NOT NULL DEFAULT '' COMMENT '关联业务单号',
  `remark` varchar(255) DEFAULT NULL COMMENT '说明',
  `add_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `add_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='储值余额流水表';

-- 5. 储值套餐
CREATE TABLE IF NOT EXISTS `tl_recharge_plan` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL COMMENT '套餐名称',
  `amount` decimal(10,2) NOT NULL COMMENT '实付金额',
  `gift_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '赠送金额',
  `gift_points` int(11) NOT NULL DEFAULT 0 COMMENT '赠送积分',
  `pic_url` varchar(255) DEFAULT NULL COMMENT '展示图',
  `sort` int(11) NOT NULL DEFAULT 0 COMMENT '排序，大的在前',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否上架',
  `add_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='储值套餐表';

-- 默认四档会员等级（数值可后台调整）
INSERT INTO `tl_member_level` (`id`,`name`,`level`,`min_growth`,`discount`,`point_rate`,`desc`,`enabled`,`add_time`,`update_time`,`deleted`) VALUES
(1,'普通会员',0,0,1.00,1.00,'注册即享，消费得积分',1,NOW(),NOW(),0),
(2,'银卡会员',1,1000,0.98,1.20,'满 1000 成长值升级，98 折，积分 1.2 倍',1,NOW(),NOW(),0),
(3,'金卡会员',2,5000,0.95,1.50,'满 5000 成长值升级，95 折，积分 1.5 倍',1,NOW(),NOW(),0),
(4,'黑卡会员',3,20000,0.90,2.00,'满 20000 成长值升级，9 折，积分 2 倍',1,NOW(),NOW(),0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`min_growth`=VALUES(`min_growth`),`discount`=VALUES(`discount`),`point_rate`=VALUES(`point_rate`);

-- 默认储值套餐
INSERT INTO `tl_recharge_plan` (`id`,`name`,`amount`,`gift_amount`,`gift_points`,`sort`,`enabled`,`add_time`,`update_time`,`deleted`) VALUES
(1,'充 200 送 20',200.00,20.00,200,30,1,NOW(),NOW(),0),
(2,'充 500 送 60',500.00,60.00,600,20,1,NOW(),NOW(),0),
(3,'充 1000 送 150',1000.00,150.00,1500,10,1,NOW(),NOW(),0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`);
