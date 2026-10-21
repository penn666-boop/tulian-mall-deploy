package org.linlinjava.litemall.db.service;

import org.linlinjava.litemall.db.dao.BalanceLogMapper;
import org.linlinjava.litemall.db.dao.MemberAccountMapper;
import org.linlinjava.litemall.db.dao.MemberLevelMapper;
import org.linlinjava.litemall.db.dao.PointsLogMapper;
import org.linlinjava.litemall.db.dao.RechargePlanMapper;
import org.linlinjava.litemall.db.domain.BalanceLog;
import org.linlinjava.litemall.db.domain.MemberAccount;
import org.linlinjava.litemall.db.domain.MemberLevel;
import org.linlinjava.litemall.db.domain.PointsLog;
import org.linlinjava.litemall.db.domain.RechargePlan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 途链茶集会员核心服务：账户、积分、成长值、储值余额、等级计算。
 *
 * 积分规则：实付金额（元，向下取整）× 当前等级积分倍率。
 * 成长值规则：实付金额（元，向下取整），可与积分倍率解耦。
 * 等级规则：成长值达到 tl_member_level.min_growth 的最高一档。
 */
@Service
public class MemberService {

    @Resource
    private MemberAccountMapper accountMapper;
    @Resource
    private MemberLevelMapper levelMapper;
    @Resource
    private PointsLogMapper pointsLogMapper;
    @Resource
    private BalanceLogMapper balanceLogMapper;
    @Resource
    private RechargePlanMapper rechargePlanMapper;

    public static final byte TYPE_POINTS_GAIN = 1;
    public static final byte TYPE_POINTS_CONSUME = 2;
    public static final byte TYPE_POINTS_EXPIRE = 3;
    public static final byte TYPE_POINTS_REFUND = 4;
    public static final byte TYPE_POINTS_ADMIN = 5;

    public static final byte TYPE_BALANCE_RECHARGE = 1;
    public static final byte TYPE_BALANCE_CONSUME = 2;
    public static final byte TYPE_BALANCE_REFUND = 3;
    public static final byte TYPE_BALANCE_ADMIN = 4;

    /**
     * 取会员账户，不存在则创建
     */
    public MemberAccount getOrCreate(Integer userId) {
        MemberAccount account = accountMapper.findByUserId(userId);
        if (account == null) {
            accountMapper.createIfAbsent(userId);
            account = accountMapper.findByUserId(userId);
        }
        return account;
    }

    public MemberLevel levelOfGrowth(Integer growth) {
        if (growth == null) {
            growth = 0;
        }
        MemberLevel level = levelMapper.findByGrowth(growth);
        if (level == null) {
            // 兜底：取最低等级
            List<MemberLevel> levels = levelMapper.queryEnabled();
            level = levels.isEmpty() ? null : levels.get(0);
        }
        return level;
    }

    /**
     * 按当前成长值算出应得等级（供展示与升级判断）
     */
    public MemberLevel levelOfUser(Integer userId) {
        MemberAccount account = getOrCreate(userId);
        return levelOfGrowth(account.getGrowth());
    }

    /**
     * 订单完成发放积分与成长值。
     *
     * @param paidAmount 实付金额（元）
     * @return 本次发放的积分
     */
    @Transactional(rollbackFor = Exception.class)
    public int grantByOrder(Integer userId, BigDecimal paidAmount, String orderSn, String remark) {
        if (paidAmount == null || paidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        MemberAccount account = getOrCreate(userId);
        MemberLevel level = levelOfGrowth(account.getGrowth());

        BigDecimal rate = level == null || level.getPointRate() == null
                ? BigDecimal.ONE : level.getPointRate();
        int base = paidAmount.setScale(0, RoundingMode.DOWN).intValue();
        int points = BigDecimal.valueOf(base).multiply(rate).setScale(0, RoundingMode.DOWN).intValue();
        int growth = base;

        if (points > 0) {
            accountMapper.addPoints(userId, points);
            MemberAccount after = accountMapper.findByUserId(userId);
            PointsLog log = new PointsLog();
            log.setUserId(userId);
            log.setType(TYPE_POINTS_GAIN);
            log.setAmount(points);
            log.setBalance(after.getPoints());
            log.setSource("order");
            log.setSourceId(orderSn);
            log.setRemark(remark);
            pointsLogMapper.create(log);
        }
        if (growth > 0) {
            accountMapper.addGrowth(userId, growth);
        }
        return points;
    }

    /**
     * 消费积分抵扣。返回是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean consumePoints(Integer userId, int points, String source, String sourceId, String remark) {
        if (points <= 0) {
            return true;
        }
        int rows = accountMapper.deductPoints(userId, points);
        if (rows == 0) {
            return false;
        }
        MemberAccount after = accountMapper.findByUserId(userId);
        PointsLog log = new PointsLog();
        log.setUserId(userId);
        log.setType(TYPE_POINTS_CONSUME);
        log.setAmount(-points);
        log.setBalance(after.getPoints());
        log.setSource(source);
        log.setSourceId(sourceId);
        log.setRemark(remark);
        pointsLogMapper.create(log);
        return true;
    }

    /**
     * 储值余额抵扣。返回是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean consumeBalance(Integer userId, BigDecimal amount, String source, String sourceId, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        int rows = accountMapper.deductBalance(userId, amount);
        if (rows == 0) {
            return false;
        }
        MemberAccount after = accountMapper.findByUserId(userId);
        BalanceLog log = new BalanceLog();
        log.setUserId(userId);
        log.setType(TYPE_BALANCE_CONSUME);
        log.setAmount(amount.negate());
        log.setBalance(after.getBalance());
        log.setSource(source);
        log.setSourceId(sourceId);
        log.setRemark(remark);
        balanceLogMapper.create(log);
        return true;
    }

    /**
     * 充值入账（含赠送金额与赠送积分）
     */
    @Transactional(rollbackFor = Exception.class)
    public MemberAccount recharge(Integer userId, BigDecimal payAmount, BigDecimal giftAmount,
                                  int giftPoints, String tradeSn) {
        getOrCreate(userId);
        BigDecimal total = payAmount.add(giftAmount == null ? BigDecimal.ZERO : giftAmount);
        accountMapper.addBalance(userId, total);

        MemberAccount after = accountMapper.findByUserId(userId);
        BalanceLog log = new BalanceLog();
        log.setUserId(userId);
        log.setType(TYPE_BALANCE_RECHARGE);
        log.setAmount(total);
        log.setBalance(after.getBalance());
        log.setSource("recharge");
        log.setSourceId(tradeSn);
        log.setRemark(giftAmount != null && giftAmount.compareTo(BigDecimal.ZERO) > 0
                ? "充值 " + payAmount + " 赠送 " + giftAmount : "充值 " + payAmount);
        balanceLogMapper.create(log);

        if (giftPoints > 0) {
            accountMapper.addPoints(userId, giftPoints);
            MemberAccount afterPoints = accountMapper.findByUserId(userId);
            PointsLog log2 = new PointsLog();
            log2.setUserId(userId);
            log2.setType(TYPE_POINTS_GAIN);
            log2.setAmount(giftPoints);
            log2.setBalance(afterPoints.getPoints());
            log2.setSource("recharge");
            log2.setSourceId(tradeSn);
            log2.setRemark("充值赠送积分");
            pointsLogMapper.create(log2);
            after = afterPoints;
        }
        return after;
    }

    /**
     * 管理员调整积分/余额（正负均可）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean adjust(Integer userId, Integer points, BigDecimal balance, String remark) {
        getOrCreate(userId);
        MemberAccount account = accountMapper.findByUserId(userId);
        int newPoints = account.getPoints() + (points == null ? 0 : points);
        BigDecimal newBalance = account.getBalance().add(balance == null ? BigDecimal.ZERO : balance);
        if (newPoints < 0 || newBalance.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        account.setPoints(newPoints);
        account.setTotalPoints(account.getTotalPoints() + Math.max(points == null ? 0 : points, 0));
        account.setBalance(newBalance);
        accountMapper.updateByUserId(account);

        if (points != null && points != 0) {
            PointsLog log = new PointsLog();
            log.setUserId(userId);
            log.setType(TYPE_POINTS_ADMIN);
            log.setAmount(points);
            log.setBalance(newPoints);
            log.setSource("admin");
            log.setRemark(remark);
            pointsLogMapper.create(log);
        }
        if (balance != null && balance.compareTo(BigDecimal.ZERO) != 0) {
            BalanceLog log = new BalanceLog();
            log.setUserId(userId);
            log.setType(TYPE_BALANCE_ADMIN);
            log.setAmount(balance);
            log.setBalance(newBalance);
            log.setSource("admin");
            log.setRemark(remark);
            balanceLogMapper.create(log);
        }
        return true;
    }

    public List<PointsLog> pointsLog(Integer userId, int page, int limit) {
        int offset = Math.max(page - 1, 0) * limit;
        return pointsLogMapper.queryByUser(userId, offset, limit);
    }

    public int pointsLogCount(Integer userId) {
        return pointsLogMapper.countByUser(userId);
    }

    public List<BalanceLog> balanceLog(Integer userId, int page, int limit) {
        int offset = Math.max(page - 1, 0) * limit;
        return balanceLogMapper.queryByUser(userId, offset, limit);
    }

    public List<MemberLevel> allLevels() {
        return levelMapper.queryEnabled();
    }

    public List<RechargePlan> enabledPlans() {
        return rechargePlanMapper.queryEnabled();
    }

    public RechargePlan findPlan(Integer planId) {
        return rechargePlanMapper.findById(planId);
    }

    public int balanceLogCount(Integer userId) {
        return balanceLogMapper.countByUser(userId);
    }
}
