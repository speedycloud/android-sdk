package com.speedycloud.spobjsdk.utils;

import android.content.Context;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
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
 * Created by gyb on 2018/4/11.
 */

public class DownHelper {
    // 获取需要下载的文件的长度
    public static int getFileLength(String fileUrl,String bucket, String obj,String accesskey,String secretkey, Context context) {
        int length = -1;
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            Map<String,String> newParams = new HashMap<>();
            newParams = getNewParams(bucket,obj,accesskey,secretkey);
            for (String key : newParams.keySet()) {
                Log.d("gyb","key=" + key + " value =" + newParams.get(key));
                connection.setRequestProperty(key,newParams.get(key));
            }
            connection.setConnectTimeout(5000);
            connection.connect();
            // 伪装成浏览器
//            connection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 5.1; Trident/4.0; .NET CLR 2.0.50727)");
            length = connection.getContentLength();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            Toast.makeText(context, "URL不正确", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return length;
    }

    public static Map<String,String> getNewParams(String bucket, String obj,String accessKey,String secretKey){
        Map<String,String> params = new HashMap<>();
        params.put("http_method","GET");
        params.put("url",bucket + "/" + obj);
        params.put("Content_Type","application/x-www-form-urlencoded");
        return generateHeaders(params,accessKey,secretKey,false);
    }
    private static String dateFormat() {
        // Fri, 09 Oct 2015 04:06:18 GMT
        Calendar cd = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT")); // 设置时区为GMT
        String str = sdf.format(cd.getTime());

        return str;
    }

    public static String genSigStr(Map<String, String> params) {
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

        String dateFormat = dateFormat();
        resultBuf.append("\n" + dateFormat);

        String canonicalized_amz_headers = params.get("canonicalized_amz_headers");
        if(canonicalized_amz_headers != null) {
            resultBuf.append("\n" + canonicalized_amz_headers);
        }

        String url = params.get("url");
        resultBuf.append("\n/" + url);

        return resultBuf.toString();
    }

    private static String hmassha1(String base,String secretKey) throws NoSuchAlgorithmException, InvalidKeyException {
        String key = secretKey;

        String type = "HmacSHA1";
        SecretKeySpec secret = new SecretKeySpec(key.getBytes(), type);

        Mac mac = Mac.getInstance(type);
        mac.init(secret);
        byte[] digest = mac.doFinal(base.getBytes());

        String sig = Base64.encodeToString(digest, Base64.DEFAULT);
        sig = sig.trim();

        return sig;
    }

    private static String genSig(String method, String secretKey, Map<String, String> params) {
        String amz = params.get("x-amz-acl");
        if(amz != null) {
            params.put("canonicalized_amz_headers", "x-amz-acl:" + amz);
        }

        String sigStr = genSigStr(params);
        try {
            String sig = hmassha1(sigStr,secretKey);
            return sig;
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("sha1", e.toString());
            return "";
        }
    }

    public static Map<String, String> generateHeaders(Map<String, String> params,String accessKey, String secretKey, boolean isJson) {
        Map<String, String> headers = new HashMap<String, String>();
        if (params == null) {
            return headers;
        }

        String method = params.get("http_method");
        if(method == null) {
            method = "GET";
        }

        String sign = genSig(method,secretKey, params);

        String authorization = "AWS " + accessKey + ":" + sign;
        String dateStr = dateFormat();

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
