package com.speedycloud.spobjsdk.http;

import com.speedycloud.spobjsdk.common.Constants;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Created by gyb on 2018/4/3.
 */

public final class ResponseInfo {
    public static final int ZeroSizeFile = -6;
    public static final int InvalidToken = -5;
    public static final int InvalidArgument = -4;
    public static final int InvalidFile = -3;
    public static final int Cancelled = -2;
    public static final int NetworkError = -1;

    public static final int UnknownError = 0;

    // <-- error code copy from ios
    public static final int TimedOut = -1001;
    public static final int UnknownHost = -1003;
    public static final int CannotConnectToHost = -1004;
    public static final int NetworkConnectionLost = -1005;

    // -->
    /**
     * 回复状态码
     */
    public final int statusCode;
    /**
     * 七牛日志扩展头
     */
    public final String reqId;

    public final String strEtag;
    /**
     * 七牛日志扩展头
     */
    public final String xlog;
    /**
     * cdn日志扩展头
     */
    public final String xvia;
    /**
     * 错误信息
     */
    public final String error;
    /**
     * 请求消耗时间，单位毫秒
     */
    public final long duration;
    /**
     * 服务器域名
     */
    public final String host;
    /**
     * 服务器IP
     */
    public final String ip;

    /**
     * 服务器端口
     */
    public final int port;

    /**
     * 访问路径
     */
    public final String path;

    /**
     * user agent id
     */
    public final String id;

    /**
     * log 时间戳
     */
    public final long timeStamp;

    /**
     * 已发送字节数
     */
    public final long sent;

//    public final UpToken upToken;

    public final long totalSize;

    /**
     * 响应体，json 格式
     */
    public final JSONObject response;

    private ResponseInfo(JSONObject json, int statusCode, String reqId, String strEtag, String xlog, String xvia, String host,
                         String path, String ip, int port, long duration, long sent, String error, long totalSize) {
        response = json;
        this.statusCode = statusCode;
        this.reqId = reqId;
        this.strEtag = strEtag;
        this.xlog = xlog;
        this.xvia = xvia;
        this.host = host;
        this.path = path;
        this.duration = duration;
        this.error = error;
        this.ip = ip;
        this.port = port;
        this.id = "";//UserAgent.instance().id;
        this.timeStamp = System.currentTimeMillis() / 1000;
        this.sent = sent;
//        this.upToken = upToken;
        this.totalSize = totalSize;
    }

    public static ResponseInfo create(final JSONObject json, final int statusCode, final String reqId,final String strEtag,
                                      final String xlog, final String xvia, final String host,
                                      final String path, final String oIp, final int port, final long duration,
                                      final long sent, final String error, final long totalSize) {

        String _ip = (oIp + "").split(":")[0];
        final String ip = _ip.substring(Math.max(0, _ip.indexOf("/") + 1));
        ResponseInfo res = new ResponseInfo(json, statusCode, reqId, strEtag, xlog, xvia, host, path, ip,
                port, duration, sent, error, totalSize);
//        if (Config.isRecord ) {
//            final String _timeStamp = res.timeStamp + "";
//            UploadInfoCollector.handleHttp(
//                    // 延迟序列化.如果判断不记录,则不执行序列化
//                    new UploadInfoCollector.RecordMsg() {
//
//                        @Override
//                        public String toRecordMsg() {
//                            String[] ss = new String[]{statusCode + "", reqId, host, ip, port + "", duration + "",
//                                    _timeStamp, sent + "", getUpType(path), totalSize + ""};
//                            return StringUtils.join(ss, ",");
//                        }
//                    });
//        }
        return res;
    }

    // 通过path ，解析出是 form, mkblk, bput, mkfile
    private static String getUpType(String path) {
        if (path == null || !path.startsWith("/")) {
            return "";
        }
        if ("/".equals(path)) {
            return "form";
        }
        int l = path.indexOf('/', 1);
        if (l < 1) {
            return "";
        }
        String m = path.substring(1, l);
        switch (m) {
            case "mkblk":
                return "mkblk";
            case "bput":
                return "bput";
            case "mkfile":
                return "mkfile";
            case "put":
                return "put";
            default:
                return "";
        }
    }

    public static ResponseInfo zeroSize() {
        return create(null, ZeroSizeFile, "", "","", "", "", "", "", 80, 0, 0, "file or data size is zero", 0);
    }

    public static ResponseInfo cancelled() {
        return create(null, Cancelled, "", "","", "", "", "", "", 80, -1, -1, "cancelled by user", 0);
    }

    public static ResponseInfo invalidArgument(String message) {
        return create(null, 0,"", "","", "", "", "", "", 80, 0, 0, message, 0);
    }

    public static ResponseInfo invalidToken(String message) {
        return create(null, 0, "", "","", "", "", "", "",80, 0, 0, message, 0);
    }

    public static ResponseInfo fileError(Exception e) {
        return create(null, InvalidFile, "", "","", "", "", "", "", 80, 0, 0, e.getMessage(), 0);
    }

    public static ResponseInfo networkError(int code) {
        return create(null, code, "", "","", "", "", "", "", 80, 0, 0, "Network error during preQuery", 0);
    }

    public static boolean isStatusCodeForBrokenNetwork(int code) {
        return code == NetworkError || code == UnknownHost
                || code == CannotConnectToHost || code == TimedOut
                || code == NetworkConnectionLost;
    }

    public boolean isCancelled() {
        return statusCode == Cancelled;
    }

    public boolean isOK() {
        return statusCode == 200 && error == null && (hasReqId() || response != null);
    }

    public boolean isNetworkBroken() {
        return statusCode == NetworkError || statusCode == UnknownHost
                || statusCode == CannotConnectToHost || statusCode == TimedOut
                || statusCode == NetworkConnectionLost;
    }

    public boolean isServerError() {
        return (statusCode >= 500 && statusCode < 600 && statusCode != 579)
                || statusCode == 996;
    }

    public boolean needSwitchServer() {
        return isNetworkBroken() || isServerError();
    }

    public boolean needRetry() {
        return !isCancelled() && (
                needSwitchServer() || statusCode == 406 ||
                        (statusCode == 200 && error != null) || isNotQiniu());
    }

    public boolean isNotQiniu() {
        return statusCode < 500 && statusCode > 200;
    }

    public String toString() {
        return String.format(Locale.ENGLISH, "{ver:%s,ResponseInfo:%s,status:%d, reqId:%s, xlog:%s, xvia:%s, host:%s, path:%s, ip:%s, port:%d, duration:%d s, time:%d, sent:%d,error:%s}",
                Constants.VERSION, id, statusCode, reqId, xlog, xvia, host, path, ip, port, duration, timeStamp, sent, error);
    }

    public boolean hasReqId() {
        return reqId != null;
    }
}
