package org.linlinjava.litemall.db.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 储值充值订单（途链茶集扩展）
 */
public class RechargeOrder {
    private Integer id;
    private Integer userId;
    private String orderSn;
    private Integer planId;
    private BigDecimal payAmount;
    private BigDecimal giftAmount;
    private Integer giftPoints;
    /** 0 待支付 1 已支付 2 已取消 */
    private Byte status;
    private String payId;
    private LocalDateTime payTime;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public static final byte STATUS_UNPAID = 0;
    public static final byte STATUS_PAID = 1;
    public static final byte STATUS_CANCEL = 2;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getOrderSn() {
        return orderSn;
    }

    public void setOrderSn(String orderSn) {
        this.orderSn = orderSn;
    }

    public Integer getPlanId() {
        return planId;
    }

    public void setPlanId(Integer planId) {
        this.planId = planId;
    }

    public BigDecimal getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(BigDecimal payAmount) {
        this.payAmount = payAmount;
    }

    public BigDecimal getGiftAmount() {
        return giftAmount;
    }

    public void setGiftAmount(BigDecimal giftAmount) {
        this.giftAmount = giftAmount;
    }

    public Integer getGiftPoints() {
        return giftPoints;
    }

    public void setGiftPoints(Integer giftPoints) {
        this.giftPoints = giftPoints;
    }

    public Byte getStatus() {
        return status;
    }

    public void setStatus(Byte status) {
        this.status = status;
    }

    public String getPayId() {
        return payId;
    }

    public void setPayId(String payId) {
        this.payId = payId;
    }

    public LocalDateTime getPayTime() {
        return payTime;
    }

    public void setPayTime(LocalDateTime payTime) {
        this.payTime = payTime;
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
