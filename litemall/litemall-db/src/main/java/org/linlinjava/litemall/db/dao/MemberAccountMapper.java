package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.MemberAccount;

import java.util.List;

/**
 * 会员账户 Mapper（途链茶集扩展）
 */
public interface MemberAccountMapper {

    @Select("SELECT * FROM tl_member_account WHERE user_id = #{userId}")
    @Results(id = "accountResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "points", property = "points"),
            @Result(column = "total_points", property = "totalPoints"),
            @Result(column = "balance", property = "balance"),
            @Result(column = "recharge_total", property = "rechargeTotal"),
            @Result(column = "growth", property = "growth"),
            @Result(column = "add_time", property = "addTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    MemberAccount findByUserId(@Param("userId") Integer userId);

    @Insert("INSERT INTO tl_member_account(user_id, points, total_points, balance, recharge_total, growth, add_time, update_time) " +
            "VALUES(#{userId}, 0, 0, 0.00, 0.00, 0, NOW(), NOW())")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "id", before = false, resultType = Integer.class)
    int create(MemberAccount account);

    @Update("UPDATE tl_member_account SET points = #{points}, total_points = #{totalPoints}, balance = #{balance}, " +
            "recharge_total = #{rechargeTotal}, growth = #{growth}, update_time = NOW() WHERE user_id = #{userId}")
    int updateByUserId(MemberAccount account);

    /**
     * 先确保账户存在，再返回。用 INSERT IGNORE 避免并发重复。
     */
    @Insert("INSERT IGNORE INTO tl_member_account(user_id, points, total_points, balance, recharge_total, growth, add_time, update_time) " +
            "VALUES(#{userId}, 0, 0, 0.00, 0.00, 0, NOW(), NOW())")
    int createIfAbsent(@Param("userId") Integer userId);

    /**
     * 原子扣减余额，amount 为正数（单位元），返回影响行数；余额不足时返回 0
     */
    @Update("UPDATE tl_member_account SET balance = balance - #{amount}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND balance >= #{amount}")
    int deductBalance(@Param("userId") Integer userId, @Param("amount") java.math.BigDecimal amount);

    /**
     * 原子扣减积分，points 为正数；积分不足时返回 0
     */
    @Update("UPDATE tl_member_account SET points = points - #{points}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND points >= #{points}")
    int deductPoints(@Param("userId") Integer userId, @Param("points") Integer points);

    /**
     * 原子增加积分与累计积分
     */
    @Update("UPDATE tl_member_account SET points = points + #{points}, total_points = total_points + #{points}, update_time = NOW() " +
            "WHERE user_id = #{userId}")
    int addPoints(@Param("userId") Integer userId, @Param("points") Integer points);

    /**
     * 原子增加余额与累计充值
     */
    @Update("UPDATE tl_member_account SET balance = balance + #{amount}, recharge_total = recharge_total + #{amount}, update_time = NOW() " +
            "WHERE user_id = #{userId}")
    int addBalance(@Param("userId") Integer userId, @Param("amount") java.math.BigDecimal amount);

    /**
     * 原子增加成长值
     */
    @Update("UPDATE tl_member_account SET growth = growth + #{growth}, update_time = NOW() WHERE user_id = #{userId}")
    int addGrowth(@Param("userId") Integer userId, @Param("growth") Integer growth);

    @Select("SELECT * FROM tl_member_account ORDER BY growth DESC LIMIT #{limit}")
    @ResultMap("accountResultMap")
    List<MemberAccount> queryTop(@Param("limit") Integer limit);
}
