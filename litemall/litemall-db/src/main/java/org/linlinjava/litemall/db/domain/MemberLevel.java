package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * 途链茶集会员等级规则
 * 对应数据库表 tl_member_level
 */
public class MemberLevel {
    private Integer id;
    private String name;
    private Byte level;
    private Integer minGrowth;
    private java.math.BigDecimal discount;
    private java.math.BigDecimal pointRate;
    private String desc;
    private Boolean enabled;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Byte getLevel() {
        return level;
    }

    public void setLevel(Byte level) {
        this.level = level;
    }

    public Integer getMinGrowth() {
        return minGrowth;
    }

    public void setMinGrowth(Integer minGrowth) {
        this.minGrowth = minGrowth;
    }

    public java.math.BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(java.math.BigDecimal discount) {
        this.discount = discount;
    }

    public java.math.BigDecimal getPointRate() {
        return pointRate;
    }

    public void setPointRate(java.math.BigDecimal pointRate) {
        this.pointRate = pointRate;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getAddTime() {
        return addTime;
    }

    public void setAddTime(LocalDateTime addTime) {
        this.addTime = addTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }
}
