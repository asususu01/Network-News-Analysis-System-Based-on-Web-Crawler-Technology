/* ============================================================
   基于网络爬虫技术的网络新闻分析系统 - 通用JS工具
   ============================================================ */

/**
 * 通用AJAX请求封装（使用jQuery）
 * @param url  请求地址
 * @param data 请求参数（对象或FormData）
 * @param type GET/POST
 * @param success 成功回调
 * @param error 失败回调
 */
function ajaxRequest(url, data, type, success, error) {
    $.ajax({
        url: url,
        type: type || "GET",
        data: data || {},
        dataType: "json",
        success: function (res) {
            if (res.code === 200) {
                if (success) success(res.data, res.msg);
            } else {
                if (res.code === 401) {
                    alert("登录已过期，请重新登录");
                    window.location.href = "/login";
                    return;
                }
                if (error) {
                    error(res.msg || "请求失败");
                } else {
                    alert(res.msg || "请求失败");
                }
            }
        },
        error: function (xhr) {
            if (xhr.status === 401) {
                alert("登录已过期，请重新登录");
                window.location.href = "/login";
                return;
            }
            var msg = "服务器异常 (" + xhr.status + ")";
            if (error) error(msg);
            else alert(msg);
        }
    });
}

/**
 * 格式化日期时间：Date或字符串 -> "YYYY-MM-DD HH:mm:ss"
 */
function formatDateTime(input) {
    if (!input) return "";
    var d = (input instanceof Date) ? input : new Date(input);
    if (isNaN(d.getTime())) return input;
    function pad(n) { return n < 10 ? "0" + n : n; }
    return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate())
        + " " + pad(d.getHours()) + ":" + pad(d.getMinutes()) + ":" + pad(d.getSeconds());
}

/**
 * 对文本做HTML转义，防止XSS
 */
function escapeHtml(text) {
    if (text == null) return "";
    return String(text)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

/**
 * 通用删除确认
 */
function confirmDelete(url, id, callback) {
    if (!confirm("确认删除该条记录？此操作不可撤销！")) return;
    ajaxRequest(url, {id: id}, "POST", function () {
        alert("删除成功");
        if (callback) callback();
        else location.reload();
    });
}
