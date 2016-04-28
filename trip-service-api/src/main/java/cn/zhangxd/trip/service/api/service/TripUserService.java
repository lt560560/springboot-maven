package cn.zhangxd.trip.service.api.service;

import cn.zhangxd.trip.service.api.vo.TripUser;

/**
 * 用户接口
 * Created by zhangxd on 16/4/18.
 */
public interface TripUserService {

    /**
     * 通过登录账号查询用户
     * @param login 登录账号
     * @return 用户
     */
    TripUser findUserByLogin(String login);
}