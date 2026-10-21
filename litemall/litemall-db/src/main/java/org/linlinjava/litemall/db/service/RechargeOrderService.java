package org.linlinjava.litemall.db.service;

import org.linlinjava.litemall.db.dao.RechargeOrderMapper;
import org.linlinjava.litemall.db.dao.RechargePlanMapper;
import org.linlinjava.litemall.db.domain.RechargeOrder;
import org.linlinjava.litemall.db.domain.RechargePlan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 储值充值订单服务（途链茶集扩展）。
 *
 * 流程：创建待支付充值单 -> 微信 JSAPI 下单 -> 支付回调 markPaid 成功后入账。
 */
@Service
public class RechargeOrderService {

    @Resource
    private RechargeOrderMapper rechargeOrderMapper;
    @Resource
    private RechargePlanMapper rechargePlanMapper;
    @Resource
    private MemberService memberService;

    private static final DateTimeFormatter SN_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public String nextOrderSn() {
        return "TLR" + LocalDateTime.now().format(SN_FMT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    /**
     * 按套餐创建充值单；套餐不存在或已停用返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public RechargeOrder createByPlan(Integer userId, Integer planId) {
        RechargePlan plan = rechargePlanMapper.findById(planId);
        if (plan == null || plan.getEnabled() == null || !plan.getEnabled()) {
            return null;
        }
        RechargeOrder order = new RechargeOrder();
        order.setUserId(userId);
        order.setOrderSn(nextOrderSn());
        order.setPlanId(plan.getId());
        order.setPayAmount(plan.getAmount());
        order.setGiftAmount(plan.getGiftAmount() == null ? BigDecimal.ZERO : plan.getGiftAmount());
        order.setGiftPoints(plan.getGiftPoints() == null ? 0 : plan.getGiftPoints());
        order.setStatus(RechargeOrder.STATUS_UNPAID);
        order.setPayId("");
        rechargeOrderMapper.create(order);
        return order;
    }

    /**
     * 自定义金额充值（1 元起，上限 50000 元）
     */
    @Transactional(rollbackFor = Exception.class)
    public RechargeOrder createCustom(Integer userId, BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        amount = amount.setScale(2, BigDecimal.ROUND_HALF_UP);
        if (amount.compareTo(BigDecimal.ONE) < 0 || amount.compareTo(new BigDecimal("50000")) > 0) {
            return null;
        }
        RechargeOrder order = new RechargeOrder();
        order.setUserId(userId);
        order.setOrderSn(nextOrderSn());
        order.setPlanId(null);
        order.setPayAmount(amount);
        order.setGiftAmount(BigDecimal.ZERO);
        order.setGiftPoints(0);
        order.setStatus(RechargeOrder.STATUS_UNPAID);
        order.setPayId("");
        rechargeOrderMapper.create(order);
        return order;
    }

    public RechargeOrder findBySn(String orderSn) {
        return rechargeOrderMapper.findBySn(orderSn);
    }

    public List<RechargeOrder> listByUser(Integer userId, int page, int limit) {
        int offset = Math.max(page - 1, 0) * limit;
        return rechargeOrderMapper.queryByUser(userId, offset, limit);
    }

    public int countByUser(Integer userId) {
        return rechargeOrderMapper.countByUser(userId);
    }

    public int cancel(String orderSn) {
        return rechargeOrderMapper.cancel(orderSn);
    }

    /**
     * 支付成功入账。markPaid 带状态条件，天然幂等：重复回调不会重复加钱。
     *
     * @return true 表示本次是首次入账
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean handlePaid(String orderSn, String payId) {
        int rows = rechargeOrderMapper.markPaid(orderSn, payId == null ? "" : payId);
        if (rows == 0) {
            return false;
        }
        RechargeOrder order = rechargeOrderMapper.findBySn(orderSn);
        if (order == null) {
            return false;
        }
        memberService.recharge(order.getUserId(), order.getPayAmount(),
                order.getGiftAmount(), order.getGiftPoints() == null ? 0 : order.getGiftPoints(),
                order.getOrderSn());
        return true;
    }
}
