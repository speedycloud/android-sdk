package com.speedycloud.os.spobjectstorage;

import android.util.Base64;
import android.util.Log;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Exchanger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okio.BufferedSink;

/**
 * Created by guohuang on 2017/4/21.
 */

public class OSNetwork {
    private String ACCESS_KEY;
    private String SECRET_KEY;
    private OkHttpClient client;
    private String BASE_SERVER;
    private Boolean DEBUG;

    public OSNetwork(String access, String secret) {
        this.ACCESS_KEY = access;
        this.SECRET_KEY = secret;
        this.BASE_SERVER = "http://cos.speedycloud.org";
        this.client = new OkHttpClient();
        this.DEBUG = true;
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

        String content_type = params.get("content_type");
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
        String key = this.SECRET_KEY;

        String type = "HmacSHA1";
        SecretKeySpec secret = new SecretKeySpec(key.getBytes(), type);

        Mac mac = Mac.getInstance(type);
        mac.init(secret);
        byte[] digest = mac.doFinal(base.getBytes());

        return Base64.encodeToString(digest, Base64.DEFAULT);
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

    private Map<String, String> generateHeaders(String method, Map<String, String> params) {
        String sign = this.genSig(method, params);

        if(this.DEBUG) {
            Log.i("sign", sign);
            Log.i("sign", "DD");
        }

        String authorization = "AWS " + this.ACCESS_KEY + ":" + sign;

        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Date", this.dateFormat());
        headers.put("Authorization", authorization);

        String acl = params.get("x-amz-acl");
        if(acl != null) {
            headers.put("x-amz-acl", acl);
        }

        String content_length = params.get("content_length");
        if(content_length != null) {
            headers.put("Content-Length", content_length);
        }

        String content_type = params.get("content_type");
        if(content_type != null) {
            headers.put("Content-Type", content_type);
        }

        return headers;
    }

    private String request(String path, String method, Map<String, String> params, RequestBody body) {
        String url = this.BASE_SERVER + "/" + path;
        if(this.DEBUG) {
            Log.i("Request", url);
        }

        Map<String, String> headers = this.generateHeaders(method, params);

        Request.Builder builder = new Request.Builder();
        builder.url(url);

        for(String key : headers.keySet()) {
            builder.addHeader(key, headers.get(key));
        }

        if(body == null && !method.equals("GET")) {
            MediaType type = MediaType.parse("");
            RequestBody data = RequestBody.create(type, "");
            builder.method(method, data);
        }

        if(body != null && !method.equals("GET")) {
            builder.method(method, body);
        }

        Request request = builder.build();

        Response response = null;
        try {
            response = client.newCall(request).execute();
            String result = response.body().string();

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("requesst", e.toString());
            return "";
        }
    }

    public String doRequest(String path, String method, Map<String, String> extendz, RequestBody body) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("url", path);
        params.put("http_method", method);

        if(extendz != null) {
            params.putAll(extendz);
        }

        return this.request(path, method, params, body);
    }
}
