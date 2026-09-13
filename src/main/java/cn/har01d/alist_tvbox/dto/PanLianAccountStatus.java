package cn.har01d.alist_tvbox.dto;

/**
 * 盘链账号池单账号状态(网页设置页展示):站点侧 {@code /api/tasks}(配额+签到)
 * 与 {@code /api/me/profile}(账号信息)的只读聚合,查询不触发签到。
 */
public record PanLianAccountStatus(
        /** 池内标识:配置的用户名或 "cookie" */
        String identity,
        /** 站点侧用户名(profile;会话失效或 Cookie 号拉不到为空) */
        String username,
        String email,
        boolean cookieBased,
        /** ok / login_failed / error */
        String status,
        /** 失败原因等附加信息 */
        String message,
        /** 本源记账:该号今日解锁配额已用尽(次日自动恢复) */
        boolean exhaustedToday,
        boolean checkinDone,
        int checkinBonus,
        int quotaRemaining,
        int quotaLimit,
        int quotaUsed) {
}
