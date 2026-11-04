package com.newssystem.common;

/**
 * 响应状态码枚举
 * 统一前后端接口状态码规范
 *
 * @author NewsSystem Dev
 */
public enum ResultCode {

    /** 成功 */
    SUCCESS(200, "操作成功"),

    /** 通用失败 */
    FAIL(500, "操作失败"),

    /** 未登录 */
    NOT_LOGIN(401, "请先登录"),

    /** 无权限 */
    FORBIDDEN(403, "无权限访问"),

    /** 参数错误 */
    PARAM_ERROR(400, "参数错误"),

    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
