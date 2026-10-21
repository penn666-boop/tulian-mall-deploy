var util = require('../../../utils/util.js');
var api = require('../../../config/api.js');

Page({
  data: {
    balance: 0,
    plans: [],
    planId: null,
    amount: '',
    selectedAmount: 0,
    giftAmount: 0,
    giftPoints: 0,
    paying: false
  },

  onShow: function () {
    this.loadAccount();
    this.loadPlans();
  },

  loadAccount: function () {
    var that = this;
    util.request(api.MemberIndex).then(function (res) {
      if (res.errno === 0) {
        that.setData({ balance: res.data.account.balance });
      } else if (res.errno === 501) {
        wx.navigateTo({ url: '/pages/auth/login/login' });
      }
    });
  },

  loadPlans: function () {
    var that = this;
    util.request(api.MemberPlans).then(function (res) {
      if (res.errno === 0) {
        that.setData({ plans: res.data || [] });
      }
    });
  },

  selectPlan: function (e) {
    var plan = e.currentTarget.dataset.plan;
    this.setData({
      planId: plan.id,
      amount: '',
      selectedAmount: plan.amount,
      giftAmount: plan.giftAmount || 0,
      giftPoints: plan.giftPoints || 0
    });
  },

  onAmountInput: function (e) {
    var val = e.detail.value;
    var num = parseFloat(val);
    this.setData({
      amount: val,
      planId: null,
      selectedAmount: isNaN(num) ? 0 : num,
      giftAmount: 0,
      giftPoints: 0
    });
  },

  submit: function () {
    var that = this;
    if (that.data.paying) {
      return;
    }
    var body;
    if (that.data.planId) {
      body = { planId: that.data.planId };
    } else {
      var num = parseFloat(that.data.amount);
      if (isNaN(num) || num < 1) {
        util.showErrorToast('请输入正确的充值金额（1 元起）');
        return;
      }
      body = { amount: num };
    }

    that.setData({ paying: true });
    wx.showLoading({ title: '正在下单...' });

    util.request(api.MemberRecharge, body, 'POST').then(function (res) {
      wx.hideLoading();
      if (res.errno !== 0) {
        that.setData({ paying: false });
        util.showErrorToast(res.errmsg || '充值下单失败');
        return;
      }
      var p = res.data.payParams;
      wx.requestPayment({
        timeStamp: p.timeStamp,
        nonceStr: p.nonceStr,
        package: p.packageValue,
        signType: p.signType,
        paySign: p.paySign,
        success: function () {
          that.setData({ paying: false });
          wx.showToast({ title: '充值成功', icon: 'success' });
          setTimeout(function () {
            wx.navigateBack();
          }, 1200);
        },
        fail: function () {
          that.setData({ paying: false });
          util.showErrorToast('支付已取消');
        }
      });
    }).catch(function () {
      wx.hideLoading();
      that.setData({ paying: false });
    });
  }
});
