var api = require('../../../config/api.js');
var util = require('../../../utils/util.js');
var user = require('../../../utils/user.js');

var app = getApp();
Page({
  data: {
    canIUseGetUserProfile: false,
  },
  onLoad: function(options) {
    // 页面初始化 options为页面跳转所带来的参数
    // 页面渲染完成
    if (wx.getUserProfile) {
      this.setData({
        canIUseGetUserProfile: true
      })
    }
  },
  onReady: function() {

  },
  onShow: function() {
    // 页面显示
  },
  onHide: function() {
    // 页面隐藏

  },
  onUnload: function() {
    // 页面关闭

  },
  wxLogin: function(e) {
    if (this.data.canIUseGetUserProfile) {
      wx.getUserProfile({
        desc: '用于完善会员资料', // 声明获取用户个人信息后的用途，后续会展示在弹窗中，请谨慎填写
        success: (res) => {
          this.doLogin(res.userInfo)
        },
        fail: () => {
          util.showErrorToast('微信登录失败');
        }
      })
    }
    else {
      if (e.detail.userInfo == undefined) {
        app.globalData.hasLogin = false;
        util.showErrorToast('微信登录失败');
        return;
      }
      this.doLogin(e.detail.userInfo)
    }
  },
  doLogin: function(userInfo) {
    user.checkLogin().catch(() => {
      user.loginByWeixin(userInfo).then(res => {
        app.globalData.hasLogin = true;
        wx.navigateBack({
          delta: 1
        })
      }).catch((err) => {
        app.globalData.hasLogin = false;
        util.showErrorToast('微信登录失败');
      });

    });
  },
  /**
   * 手机号快捷登录
   *
   * 由 <button open-type="getPhoneNumber"> 触发，回调里拿到手机号 code，
   * 再调 wx.login 拿登录 code，两个 code 一起提交后端完成登录/注册 + 绑定手机号。
   */
  phoneLogin: function(e) {
    let that = this;
    let detail = e.detail || {};

    // 用户拒绝授权或组件异常
    if (!detail.code) {
      if (detail.errMsg && detail.errMsg.indexOf('deny') >= 0) {
        util.showErrorToast('需要授权手机号才能登录');
      } else {
        util.showErrorToast('手机号获取失败：' + (detail.errMsg || '未知错误'));
      }
      return;
    }

    let phoneCode = detail.code;

    wx.login({
      success: function(loginRes) {
        if (!loginRes.code) {
          util.showErrorToast('微信登录失败');
          return;
        }

        util.request(api.AuthLoginByPhone, {
          code: loginRes.code,
          phoneCode: phoneCode
        }, 'POST').then(res => {
          if (res.errno === 0) {
            wx.setStorageSync('userInfo', res.data.userInfo);
            wx.setStorageSync('token', res.data.token);
            app.globalData.hasLogin = true;
            wx.navigateBack({
              delta: 1
            });
          } else {
            util.showErrorToast(res.errmsg || '手机号登录失败');
          }
        }).catch((err) => {
          util.showErrorToast('手机号登录失败');
        });
      },
      fail: function() {
        util.showErrorToast('微信登录失败');
      }
    });
  },
  accountLogin: function() {
    wx.navigateTo({
      url: "/pages/auth/accountLogin/accountLogin"
    });
  }
})