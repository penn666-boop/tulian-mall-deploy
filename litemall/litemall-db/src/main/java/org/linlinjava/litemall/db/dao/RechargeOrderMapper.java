package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.RechargeOrder;

import java.util.List;

/**
 * 储值充值订单 Mapper（途链茶集扩展）
 */
public interface RechargeOrderMapper {

    @Select("SELECT * FROM tl_recharge_order WHERE deleted = 0")
    @Results(id = "rechargeOrderResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "order_sn", property = "orderSn"),
            @Result(column = "plan_id", property = "planId"),
            @Result(column = "pay_amount", property = "payAmount"),
            @Result(column = "gift_amount", property = "giftAmount"),
            @Result(column = "gift_points", property = "giftPoints"),
            @Result(column = "status", property = "status"),
            @Result(column = "pay_id", property = "payId"),
            @Result(column = "pay_time", property = "payTime"),
            @Result(column = "add_time", property = "addTime"),
            @Result(column = "update_time", property = "updateTime"),
            @Result(column = "deleted", property = "deleted")
    })
    List<RechargeOrder> queryAll();

    @Select("SELECT * FROM tl_recharge_order WHERE order_sn = #{orderSn} AND deleted = 0 LIMIT 1")
    @ResultMap("rechargeOrderResultMap")
    RechargeOrder findBySn(@Param("orderSn") String orderSn);

    @Select("SELECT * FROM tl_recharge_order WHERE id = #{id} AND deleted = 0 LIMIT 1")
    @ResultMap("rechargeOrderResultMap")
    RechargeOrder findById(@Param("id") Integer id);

    @Select("SELECT * FROM tl_recharge_order WHERE user_id = #{userId} AND deleted = 0 ORDER BY id DESC LIMIT #{offset}, #{limit}")
    @ResultMap("rechargeOrderResultMap")
    List<RechargeOrder> queryByUser(@Param("userId") Integer userId,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM tl_recharge_order WHERE user_id = #{userId} AND deleted = 0")
    int countByUser(@Param("userId") Integer userId);

    @Insert("INSERT INTO tl_recharge_order(user_id, order_sn, plan_id, pay_amount, gift_amount, gift_points, status, pay_id, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{orderSn}, #{planId}, #{payAmount}, #{giftAmount}, #{giftPoints}, #{status}, #{payId}, NOW(), NOW(), 0)")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "id", before = false, resultType = Integer.class)
    int create(RechargeOrder order);

    /**
     * 标记已支付：只有待支付状态才能更新，用于并发去重
     */
    @Update("UPDATE tl_recharge_order SET status = 1, pay_id = #{payId}, pay_time = NOW(), update_time = NOW() " +
            "WHERE order_sn = #{orderSn} AND status = 0 AND deleted = 0")
    int markPaid(@Param("orderSn") String orderSn, @Param("payId") String payId);

    @Update("UPDATE tl_recharge_order SET status = 2, update_time = NOW() WHERE order_sn = #{orderSn} AND status = 0 AND deleted = 0")
    int cancel(@Param("orderSn") String orderSn);
}
