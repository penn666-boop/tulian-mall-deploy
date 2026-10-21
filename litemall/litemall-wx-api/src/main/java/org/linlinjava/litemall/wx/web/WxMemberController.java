package org.linlinjava.litemall.wx.web;

import com.github.binarywang.wxpay.bean.notify.WxPayNotifyResponse;
import com.github.binarywang.wxpay.bean.notify.WxPayOrderNotifyResult;
import com.github.binarywang.wxpay.bean.order.WxPayMpOrderResult;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderRequest;
import com.github.binarywang.wxpay.bean.result.BaseWxPayResult;
import com.github.binarywang.wxpay.constant.WxPayConstants;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.util.IpUtil;
import org.linlinjava.litemall.core.util.JacksonUtil;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.BalanceLog;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.domain.MemberAccount;
import org.linlinjava.litemall.db.domain.MemberLevel;
import org.linlinjava.litemall.db.domain.PointsLog;
import org.linlinjava.litemall.db.domain.RechargeOrder;
import org.linlinjava.litemall.db.domain.RechargePlan;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.linlinjava.litemall.db.service.MemberService;
import org.linlinjava.litemall.db.service.RechargeOrderService;
import org.linlinjava.litemall.wx.annotation.LoginUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 途链茶集会员服务
 * <p>
 * 提供会员账户概览、等级、积分明细、储值明细、套餐查询、储值充值（微信支付）。
 */
@RestController
@RequestMapping("/wx/member")
@Validated
public class WxMemberController {
    private final Log logger = LogFactory.getLog(WxMemberController.class);

    @Resource
    private MemberService memberService;
    @Resource
    private RechargeOrderService rechargeOrderService;
    @Resource
    private LitemallUserService userService;
    @Resource
    private WxPayService wxPayService;

    @Value("${litemall.wx.recharge-notify-url:}")
    private String rechargeNotifyUrl;

    /**
     * 会员中心首页数据：账户、当前等级、下一等级与升级进度
     */
    @GetMapping("index")
    public Object index(@LoginUser Integer userId) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        MemberAccount account = memberService.getOrCreate(userId);
        MemberLevel current = memberService.levelOfGrowth(account.getGrowth());

        MemberLevel next = null;
        int needGrowth = 0;
        int progress = 100;
        if (current != null) {
            List<MemberLevel> levels = memberService.allLevels();
            for (MemberLevel level : levels) {
                if (level.getMinGrowth() != null && level.getMinGrowth() > account.getGrowth()) {
                    next = level;
                    break;
                }
            }
            if (next != null && next.getMinGrowth() != null) {
                needGrowth = next.getMinGrowth() - account.getGrowth();
                int span = next.getMinGrowth() - (current.getMinGrowth() == null ? 0 : current.getMinGrowth());
                int done = account.getGrowth() - (current.getMinGrowth() == null ? 0 : current.getMinGrowth());
                progress = span <= 0 ? 100 : Math.min(100, Math.max(0, done * 100 / span));
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("account", account);
        data.put("level", current);
        data.put("nextLevel", next);
        data.put("needGrowth", needGrowth);
        data.put("progress", progress);
        return ResponseUtil.ok(data);
    }

    /**
     * 会员等级列表
     */
    @GetMapping("levels")
    public Object levels() {
        return ResponseUtil.ok(memberService.allLevels());
    }

    /**
     * 积分明细
     */
    @GetMapping("points")
    public Object points(@LoginUser Integer userId,
                         @RequestParam(defaultValue = "1") Integer page,
                         @RequestParam(defaultValue = "20") Integer limit) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        List<PointsLog> list = memberService.pointsLog(userId, page, limit);
        int total = memberService.pointsLogCount(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        return ResponseUtil.ok(data);
    }

    /**
     * 储值余额明细
     */
    @GetMapping("balance")
    public Object balance(@LoginUser Integer userId,
                          @RequestParam(defaultValue = "1") Integer page,
                          @RequestParam(defaultValue = "20") Integer limit) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        List<BalanceLog> list = memberService.balanceLog(userId, page, limit);
        int total = memberService.balanceLogCount(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        return ResponseUtil.ok(data);
    }

    /**
     * 储值套餐（充值入口用）
     */
    @GetMapping("plans")
    public Object plans(@LoginUser Integer userId) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        List<RechargePlan> plans = memberService.enabledPlans();
        return ResponseUtil.ok(plans);
    }

    /**
     * 发起储值充值（微信 JSAPI 支付）
     * <p>
     * body: { planId: 1 } 或 { amount: 300 }
     */
    @PostMapping("recharge")
    public Object recharge(@LoginUser Integer userId, @RequestBody String body, HttpServletRequest request) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        Integer planId = JacksonUtil.parseInteger(body, "planId");
        String amountStr = JacksonUtil.parseString(body, "amount");

        RechargeOrder order;
        if (planId != null) {
            order = rechargeOrderService.createByPlan(userId, planId);
        } else if (amountStr != null && !amountStr.trim().isEmpty()) {
            BigDecimal amount;
            try {
                amount = new BigDecimal(amountStr.trim());
            } catch (NumberFormatException e) {
                return ResponseUtil.badArgumentValue();
            }
            order = rechargeOrderService.createCustom(userId, amount);
        } else {
            return ResponseUtil.badArgument();
        }
        if (order == null) {
            return ResponseUtil.fail(732, "充值套餐不可用或金额超出范围");
        }

        LitemallUser user = userService.findById(userId);
        String openid = user == null ? null : user.getWeixinOpenid();
        if (openid == null || openid.isEmpty()) {
            return ResponseUtil.fail(403, "请先授权登录后再充值");
        }

        WxPayMpOrderResult result;
        try {
            WxPayUnifiedOrderRequest orderRequest = new WxPayUnifiedOrderRequest();
            orderRequest.setOutTradeNo(order.getOrderSn());
            orderRequest.setOpenid(openid);
            orderRequest.setBody("途链茶集储值充值");
            orderRequest.setTotalFee(order.getPayAmount().multiply(new BigDecimal(100)).intValue());
            orderRequest.setSpbillCreateIp(IpUtil.getIpAddr(request));
            if (rechargeNotifyUrl != null && !rechargeNotifyUrl.isEmpty()) {
                orderRequest.setNotifyUrl(rechargeNotifyUrl);
            }
            result = wxPayService.createOrder(orderRequest);
        } catch (Exception e) {
            logger.error("储值充值下单失败 orderSn=" + order.getOrderSn(), e);
            rechargeOrderService.cancel(order.getOrderSn());
            return ResponseUtil.fail(733, "充值下单失败，请稍后重试");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("orderSn", order.getOrderSn());
        data.put("payAmount", order.getPayAmount());
        data.put("giftAmount", order.getGiftAmount());
        data.put("giftPoints", order.getGiftPoints());
        data.put("payParams", result);
        return ResponseUtil.ok(data);
    }

    /**
     * 储值充值支付回调
     */
    @PostMapping("recharge-notify")
    public Object rechargeNotify(HttpServletRequest request, HttpServletResponse response) {
        String xmlResult;
        try {
            xmlResult = IOUtils.toString(request.getInputStream(), request.getCharacterEncoding());
        } catch (IOException e) {
            logger.error("读取充值回调失败", e);
            return WxPayNotifyResponse.fail(e.getMessage());
        }

        WxPayOrderNotifyResult result;
        try {
            result = wxPayService.parseOrderNotifyResult(xmlResult);
            if (!WxPayConstants.ResultCode.SUCCESS.equals(result.getResultCode())
                    || !WxPayConstants.ResultCode.SUCCESS.equals(result.getReturnCode())) {
                logger.error("充值回调非成功状态：" + xmlResult);
                return WxPayNotifyResponse.fail("微信通知支付失败");
            }
        } catch (WxPayException e) {
            logger.error("充值回调解签失败", e);
            return WxPayNotifyResponse.fail(e.getMessage());
        }

        String orderSn = result.getOutTradeNo();
        RechargeOrder order = rechargeOrderService.findBySn(orderSn);
        if (order == null) {
            return WxPayNotifyResponse.fail("充值单不存在 sn=" + orderSn);
        }

        String totalFee = BaseWxPayResult.fenToYuan(result.getTotalFee());
        if (!totalFee.equals(order.getPayAmount().toString())) {
            return WxPayNotifyResponse.fail(orderSn + " : 充值金额不符 totalFee=" + totalFee);
        }

        boolean first = rechargeOrderService.handlePaid(orderSn, result.getTransactionId());
        logger.info("储值充值回调处理完成 sn=" + orderSn + " 首次入账=" + first);
        return WxPayNotifyResponse.success("处理成功!");
    }

    /**
     * 充值记录
     */
    @GetMapping("recharge/orders")
    public Object rechargeOrders(@LoginUser Integer userId,
                                 @RequestParam(defaultValue = "1") Integer page,
                                 @RequestParam(defaultValue = "20") Integer limit) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        List<RechargeOrder> list = rechargeOrderService.listByUser(userId, page, limit);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", rechargeOrderService.countByUser(userId));
        return ResponseUtil.ok(data);
    }
}
