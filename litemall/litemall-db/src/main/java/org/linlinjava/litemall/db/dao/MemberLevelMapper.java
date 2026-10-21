package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.MemberLevel;

import java.util.List;

/**
 * 会员等级规则 Mapper（途链茶集扩展）
 */
public interface MemberLevelMapper {

    @Select("SELECT * FROM tl_member_level WHERE deleted = 0 ORDER BY level ASC, min_growth ASC")
    @Results(id = "levelResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "name", property = "name"),
            @Result(column = "level", property = "level"),
            @Result(column = "min_growth", property = "minGrowth"),
            @Result(column = "discount", property = "discount"),
            @Result(column = "point_rate", property = "pointRate"),
            @Result(column = "desc", property = "desc"),
            @Result(column = "enabled", property = "enabled"),
            @Result(column = "add_time", property = "addTime"),
            @Result(column = "update_time", property = "updateTime"),
            @Result(column = "deleted", property = "deleted")
    })
    List<MemberLevel> queryAll();

    @Select("SELECT * FROM tl_member_level WHERE enabled = 1 AND deleted = 0 ORDER BY min_growth ASC")
    @ResultMap("levelResultMap")
    List<MemberLevel> queryEnabled();

    @Select("SELECT * FROM tl_member_level WHERE level = #{level} AND deleted = 0 LIMIT 1")
    @ResultMap("levelResultMap")
    MemberLevel findByLevel(@Param("level") Byte level);

    @Select("SELECT * FROM tl_member_level WHERE id = #{id} AND deleted = 0 LIMIT 1")
    @ResultMap("levelResultMap")
    MemberLevel findById(@Param("id") Integer id);

    /**
     * 按成长值反查应得的等级：取所有成长值门槛不超过当前值的最高一档
     */
    @Select("SELECT * FROM tl_member_level WHERE enabled = 1 AND deleted = 0 AND min_growth <= #{growth} " +
            "ORDER BY min_growth DESC LIMIT 1")
    @ResultMap("levelResultMap")
    MemberLevel findByGrowth(@Param("growth") Integer growth);

    @Update("UPDATE tl_member_level SET name = #{name}, min_growth = #{minGrowth}, discount = #{discount}, " +
            "point_rate = #{pointRate}, `desc` = #{desc}, enabled = #{enabled}, update_time = NOW() WHERE id = #{id}")
    int update(MemberLevel level);

    @Insert("INSERT INTO tl_member_level(name, level, min_growth, discount, point_rate, `desc`, enabled, add_time, update_time, deleted) " +
            "VALUES(#{name}, #{level}, #{minGrowth}, #{discount}, #{pointRate}, #{desc}, #{enabled}, NOW(), NOW(), 0)")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "id", before = false, resultType = Integer.class)
    int create(MemberLevel level);

    @Update("UPDATE tl_member_level SET deleted = 1, update_time = NOW() WHERE id = #{id}")
    int logicalDelete(@Param("id") Integer id);
}
