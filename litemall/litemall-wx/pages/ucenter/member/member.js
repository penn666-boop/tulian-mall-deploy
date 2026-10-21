var util = require('../../../utils/util.js');
var api = require('../../../config/api.js');

Page({
  data: {
    loading: true,
    account: { points: 0, balance: 0, totalPoints: 0, rechargeTotal: 0, growth: 0 },
    level: null,
    nextLevel: null,
    needGrowth: 0,
    progress: 0,
    levels: []
  },

  onShow: function () {
    this.loadData();
    this.loadLevels();
  },

  loadData: function () {
    var that = this;
    util.request(api.MemberIndex).then(function (res) {
      if (res.errno === 0) {
        var d = res.data;
        that.setData({
          loading: false,
          account: d.account,
          level: d.level,
          nextLevel: d.nextLevel,
          needGrowth: d.needGrowth,
          progress: d.progress
        });
      } else if (res.errno === 501) {
        wx.navigateTo({ url: '/pages/auth/login/login' });
      } else {
        that.setData({ loading: false });
        util.showErrorToast(res.errmsg);
      }
    }).catch(function () {
      that.setData({ loading: false });
    });
  },

  loadLevels: function () {
    var that = this;
    util.request(api.MemberLevels).then(function (res) {
      if (res.errno === 0) {
        that.setData({ levels: res.data });
      }
    });
  },

  goRecharge: function () {
    wx.navigateTo({ url: '/pages/ucenter/recharge/recharge' });
  },

  goPoints: function () {
    wx.navigateTo({ url: '/pages/ucenter/points/points' });
  },

  goBalance: function () {
    wx.navigateTo({ url: '/pages/ucenter/balance/balance' });
  },

  goShopping: function () {
    wx.switchTab({ url: '/pages/category/category' });
  }
});
