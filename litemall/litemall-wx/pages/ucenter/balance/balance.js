var util = require('../../../utils/util.js');
var api = require('../../../config/api.js');

var TYPE_TEXT = {
  1: '储值充值',
  2: '余额消费',
  3: '退款返还',
  4: '后台调整'
};

Page({
  data: {
    balance: 0,
    rechargeTotal: 0,
    list: [],
    page: 1,
    limit: 20,
    total: 0,
    loading: false,
    finished: false
  },

  onShow: function () {
    this.loadAccount();
    this.loadList(true);
  },

  loadAccount: function () {
    var that = this;
    util.request(api.MemberIndex).then(function (res) {
      if (res.errno === 0) {
        that.setData({
          balance: res.data.account.balance,
          rechargeTotal: res.data.account.rechargeTotal
        });
      }
    });
  },

  loadList: function (reset) {
    var that = this;
    if (that.data.loading || (!reset && that.data.finished)) {
      return;
    }
    var page = reset ? 1 : that.data.page;
    that.setData({ loading: true });
    util.request(api.MemberBalance, { page: page, limit: that.data.limit }).then(function (res) {
      if (res.errno === 0) {
        var rows = res.data.list || [];
        rows.forEach(function (it) {
          it.typeText = TYPE_TEXT[it.type] || '余额变动';
          it.timeText = (it.addTime || '').replace('T', ' ').substring(0, 16);
        });
        var list = reset ? rows : that.data.list.concat(rows);
        that.setData({
          list: list,
          total: res.data.total,
          page: page + 1,
          loading: false,
          finished: list.length >= res.data.total
        });
      } else {
        that.setData({ loading: false });
      }
    }).catch(function () {
      that.setData({ loading: false });
    });
  },

  goRecharge: function () {
    wx.navigateTo({ url: '/pages/ucenter/recharge/recharge' });
  },

  onReachBottom: function () {
    this.loadList(false);
  }
});
