-- 途链茶集：储值充值订单
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `tl_recharge_order` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `order_sn` varchar(32) NOT NULL COMMENT '充值单号（用于微信 out_trade_no）',
  `plan_id` int(11) DEFAULT NULL COMMENT '套餐ID，自定义金额时为空',
  `pay_amount` decimal(10,2) NOT NULL COMMENT '实付金额',
  `gift_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '赠送金额',
  `gift_points` int(11) NOT NULL DEFAULT '0' COMMENT '赠送积分',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0待支付 1已支付 2已取消',
  `pay_id` varchar(64) DEFAULT '' COMMENT '微信交易号',
  `pay_time` datetime DEFAULT NULL,
  `add_time` datetime NOT NULL,
  `update_time` datetime NOT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_sn` (`order_sn`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='储值充值订单';
