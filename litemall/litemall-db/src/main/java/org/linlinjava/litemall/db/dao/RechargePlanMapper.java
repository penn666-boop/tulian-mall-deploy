package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.RechargePlan;

import java.util.List;

/**
 * 储值套餐 Mapper（途链茶集扩展）
 */
public interface RechargePlanMapper {

    @Select("SELECT * FROM tl_recharge_plan WHERE deleted = 0 ORDER BY sort DESC, amount ASC")
    @Results(id = "planResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "name", property = "name"),
            @Result(column = "amount", property = "amount"),
            @Result(column = "gift_amount", property = "giftAmount"),
            @Result(column = "gift_points", property = "giftPoints"),
            @Result(column = "pic_url", property = "picUrl"),
            @Result(column = "sort", property = "sort"),
            @Result(column = "enabled", property = "enabled"),
            @Result(column = "add_time", property = "addTime"),
            @Result(column = "update_time", property = "updateTime"),
            @Result(column = "deleted", property = "deleted")
    })
    List<RechargePlan> queryAll();

    @Select("SELECT * FROM tl_recharge_plan WHERE enabled = 1 AND deleted = 0 ORDER BY sort DESC, amount ASC")
    @ResultMap("planResultMap")
    List<RechargePlan> queryEnabled();

    @Select("SELECT * FROM tl_recharge_plan WHERE id = #{id} AND deleted = 0 LIMIT 1")
    @ResultMap("planResultMap")
    RechargePlan findById(@Param("id") Integer id);

    @Insert("INSERT INTO tl_recharge_plan(name, amount, gift_amount, gift_points, pic_url, sort, enabled, add_time, update_time, deleted) " +
            "VALUES(#{name}, #{amount}, #{giftAmount}, #{giftPoints}, #{picUrl}, #{sort}, #{enabled}, NOW(), NOW(), 0)")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "id", before = false, resultType = Integer.class)
    int create(RechargePlan plan);

    @Update("UPDATE tl_recharge_plan SET name = #{name}, amount = #{amount}, gift_amount = #{giftAmount}, " +
            "gift_points = #{giftPoints}, pic_url = #{picUrl}, sort = #{sort}, enabled = #{enabled}, update_time = NOW() " +
            "WHERE id = #{id}")
    int update(RechargePlan plan);

    @Update("UPDATE tl_recharge_plan SET deleted = 1, update_time = NOW() WHERE id = #{id}")
    int logicalDelete(@Param("id") Integer id);
}
