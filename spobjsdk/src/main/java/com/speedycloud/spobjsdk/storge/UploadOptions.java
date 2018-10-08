package com.speedycloud.spobjsdk.storge;

import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.speedycloud.spobjsdk.utils.AndroidNetwork;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by gyb on 2018/4/3.
 */

public final class UploadOptions {
    /**
     * 指定上传url
     */
    final String baseServer;
    /**
     * 指定上传url
     */
    final String accessKey;
    /**
     * 指定上传url
     */
    final String secretKey;
    /**
     * 扩展参数，以<code>x:</code>开头的用户自定义参数
     */
    final Map<String, String> params;

    /**
     * 指定上传文件的MimeType
     */
    final String mimeType;

    /**
     * 启用上传内容crc32校验
     */
    final boolean checkCrc;

    /**
     * 上传内容进度处理
     */
    final UpProgressHandler progressHandler;

    /**
     * 取消上传信号
     */
    final UpCancellationSignal cancellationSignal;

    /**
     * 当网络暂时无法使用时，由用户决定是否继续处理
     */
    final NetReadyHandler netReadyHandler;

    public UploadOptions(Map<String, String> params, String accesskey, String secretkey, String mimeType, boolean checkCrc,
                         UpProgressHandler progressHandler, UpCancellationSignal cancellationSignal) {
        this(params, accesskey, secretkey, mimeType, checkCrc, progressHandler, cancellationSignal, null);
    }

    public UploadOptions(Map<String, String> params, String accesskey, String secretkey, String mimeType, boolean checkCrc,
                         UpProgressHandler progressHandler, UpCancellationSignal cancellationSignal, NetReadyHandler netReadyHandler) {
        this.params = filterParam(params);
        this.baseServer = "http://oss-cn-beijing.speedycloud.org";
        this.accessKey = accesskey;
        this.secretKey = secretkey;
        this.mimeType = mime(mimeType);
        this.checkCrc = checkCrc;
        this.progressHandler = progressHandler != null ? progressHandler : new UpProgressHandler() {
            @Override
            public void progress(String key, long totalSize, double percent) {
                Log.d("Qiniu.UploadProgress", "" + percent + " totalSize =" + totalSize);
            }
        };
        this.cancellationSignal = cancellationSignal != null ? cancellationSignal : new UpCancellationSignal() {
            @Override
            public boolean isCancelled() {
                return false;
            }
        };
        this.netReadyHandler = netReadyHandler != null ? netReadyHandler : new NetReadyHandler() {
            @Override
            public void waitReady() {
                if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
                    return;
                }
                for (int i = 0; i < 6; i++) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (AndroidNetwork.isNetWorkReady()) {
                        return;
                    }
                }
            }
        };
    }

    public void addExtensParams(Map<String,String> params){
        this.params.putAll(this.generateHeaders(params,true));
    }

    /**
     * 过滤用户自定义参数，只有参数名以<code>x:</code>开头的参数才会被使用
     *
     * @param params 待过滤的用户自定义参数
     * @return 过滤后的用户自定义参数
     */
    private static Map<String, String> filterParam(Map<String, String> params) {
        Map<String, String> ret = new HashMap<String, String>();
        if (params == null) {
            return ret;
        }

        for (Map.Entry<String, String> i : params.entrySet()) {
            if(i.getValue() != null && !i.getValue().equals("") && !i.getKey().equals("method")){
                ret.put(i.getKey(), i.getValue());
            }
        }

        return ret;
    }

    static UploadOptions defaultOptions() {
        return new UploadOptions(null, null,null,null, false, null, null);
    }

    private static String mime(String mimeType) {
        if (mimeType == null || mimeType.equals("")) {
            return "application/x-www-form-urlencoded";
        }
        return mimeType;
    }

    private String dateFormat() {
        // Fri, 09 Oct 2015 04:06:18 GMT
        Calendar cd = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT")); // 设置时区为GMT
        String str = sdf.format(cd.getTime());

        return str;
    }

    public String genSigStr(Map<String, String> params) {
        StringBuffer resultBuf = new StringBuffer();

        String method = params.get("http_method");
        resultBuf.append(method);

        String content_md5 = params.get("content_md5");
        if(content_md5 == null) {
            content_md5 = "";
        }
        resultBuf.append("\n" + content_md5);

        String content_type = params.get("Content-Type");
        if(content_type == null) {
            content_type = "";
        }
        resultBuf.append("\n" + content_type);

        String dateFormat = this.dateFormat();
        resultBuf.append("\n" + dateFormat);

        String canonicalized_amz_headers = params.get("canonicalized_amz_headers");
        if(canonicalized_amz_headers != null) {
            resultBuf.append("\n" + canonicalized_amz_headers);
        }

        String url = params.get("url");
        resultBuf.append("\n/" + url);

        return resultBuf.toString();
    }

    private String hmassha1(String base) throws NoSuchAlgorithmException, InvalidKeyException {
        String key = this.secretKey;

        String type = "HmacSHA1";
        SecretKeySpec secret = new SecretKeySpec(key.getBytes(), type);

        Mac mac = Mac.getInstance(type);
        mac.init(secret);
        byte[] digest = mac.doFinal(base.getBytes());

        String sig = Base64.encodeToString(digest, Base64.DEFAULT);
        sig = sig.trim();

        return sig;
    }

    private String genSig(String method, Map<String, String> params) {
        String amz = params.get("x-amz-acl");
        if(amz != null) {
            params.put("canonicalized_amz_headers", "x-amz-acl:" + amz);
        }

        String sigStr = this.genSigStr(params);
        try {
            String sig = this.hmassha1(sigStr);
            return sig;
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("sha1", e.toString());
            return "";
        }
    }

    public Map<String, String> generateHeaders(Map<String, String> params, boolean isJson) {
        Map<String, String> headers = new HashMap<String, String>();
        if (params == null) {
            return headers;
        }

        String method = params.get("http_method");
        if(method == null) {
            method = "GET";
        }

        String sign = this.genSig(method, params);

        String authorization = "AWS " + this.accessKey + ":" + sign;
        String dateStr = this.dateFormat();

        headers.put("Date", dateStr);
        headers.put("Authorization", authorization);

        String acl = params.get("x-amz-acl");
        if(acl != null) {
            headers.put("x-amz-acl", acl);
        }

        String content_length = params.get("Content-Length");
        if(content_length != null) {
            headers.put("Content-Length", content_length);
        }

        String content_type = params.get("Content-Type");
        if(content_type != null) {
            headers.put("Content-Type", content_type);
        }

        if(isJson) {
            headers.put("Accept-Encoding", "");
        }

        return headers;
    }
}
