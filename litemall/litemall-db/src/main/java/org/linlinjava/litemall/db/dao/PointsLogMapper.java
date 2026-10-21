package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.PointsLog;

import java.util.List;

/**
 * 积分流水 Mapper（途链茶集扩展）
 */
public interface PointsLogMapper {

    @Insert("INSERT INTO tl_points_log(user_id, type, amount, balance, source, source_id, remark, add_time) " +
            "VALUES(#{userId}, #{type}, #{amount}, #{balance}, #{source}, #{sourceId}, #{remark}, NOW())")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "id", before = false, resultType = Integer.class)
    int create(PointsLog log);

    @Select("SELECT * FROM tl_points_log WHERE user_id = #{userId} ORDER BY add_time DESC, id DESC LIMIT #{offset}, #{limit}")
    @Results(id = "pointsResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "type", property = "type"),
            @Result(column = "amount", property = "amount"),
            @Result(column = "balance", property = "balance"),
            @Result(column = "source", property = "source"),
            @Result(column = "source_id", property = "sourceId"),
            @Result(column = "remark", property = "remark"),
            @Result(column = "add_time", property = "addTime")
    })
    List<PointsLog> queryByUser(@Param("userId") Integer userId, @Param("offset") Integer offset, @Param("limit") Integer limit);

    @Select("SELECT COUNT(*) FROM tl_points_log WHERE user_id = #{userId}")
    int countByUser(@Param("userId") Integer userId);
}
