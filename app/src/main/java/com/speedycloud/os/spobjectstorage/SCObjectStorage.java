package com.speedycloud.os.spobjectstorage;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.RequestBody;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by guohuang on 2017/4/21.
 */

public class SCObjectStorage {
    private OSNetwork network;
    private String ACCESS;
    private String SECRET;

    private static SCObjectStorage _inst;

    public static SCObjectStorage getInstance() {
        if(_inst == null) {
           _inst = new SCObjectStorage();
        }

        return _inst;
    }

    public void init(String access, String secret) {
        this.ACCESS = access;
        this.SECRET = secret;

        this.network = new OSNetwork(access, secret);
    }

    // 创建桶
    public String createBucket(String bucket) {
        return network.doRequest(bucket, "PUT", null, null);
    }

    // 删除桶
    public String deleteBucket(String bucket) {
        return network.doRequest(bucket, "DELETE", null, null);
    }

    // 查询桶存储桶的权限
    public String queryBucketAcl(String bucket) {
        String path = bucket + "?acl";
        return network.doRequest(path, "GET", null, null);
    }

    // 设置存储桶的版本控制
    public String setBucketVersioning(String bucket, String status) {
        if(status.equals("Enabled") || status.equals("Suspended")) {
            String path = bucket + "?versioning";
            String bodyStr = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<VersioningConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">\n<Status>" +
                    status + "</Status>\\n</VersioningConfiguration>";

            MediaType type = MediaType.parse("");
            RequestBody body = RequestBody.create(type, bodyStr);

            return network.doRequest(path, "PUT", null, body);
        }
        return "";
    }

    // 查询存储桶的版本信息
    public String queryBucketVersioning(String bucket) {
        String path = bucket + "?versioning";
        return network.doRequest(path, "GET", null, null);
    }

    // 在存储桶内创建对象（上传小文件，小于100M）
    public String createObject(String bucket, String key, RequestBody body) {
        String path = bucket + "/" + key;

        try {
            String length = Long.toString(body.contentLength());

            Map<String, String> extendz = new HashMap<String, String>();
            extendz.put("content_type", "application/x-www-form-urlencoded");
            extendz.put("content_length", length);

            return network.doRequest(path, "PUT", extendz, body);
        } catch (IOException e) {
            return "";
        }
    }

    // 删除存储桶内的对象
    public String deleteObject(String bucket, String key) {
        String path = bucket + "/" + key;
        return network.doRequest(path, "DELETE", null, null);
    }

    // 查询存储桶内所有对象的版本信息
    public String queryAllObjects(String bucket) {
        return network.doRequest(bucket, "GET", null, null);
    }

    // 删除存储桶内指定版本的对象
    public String deleteObjectVersion(String bucket, String key, String version) {
        String path = bucket + "/" + key + "?versionId=" + version;
        return network.doRequest(path, "DELETE", null, null);
    }

    // 修改存储桶内对象的权限
    public String updateObjectAcl(String bucket, String key, String acl) {
        if(acl.equals("private") || acl.equals("public-read") || acl.equals("public-read-write")) {
            Map<String, String> ext = new HashMap<String, String>();
            ext.put("x-amz-acl", acl);

            String path = bucket + "/" + key + "?acl";
            return network.doRequest(path, "PUT", ext, null);
        }
        return "";
    }

    // 修改存储桶内指定版本的对象的权限
    public String updateObjectVersionAcl(String bucket, String key, String version, String acl) {
        if(acl.equals("private") || acl.equals("public-read") || acl.equals("public-read-write")) {
            Map<String, String> ext = new HashMap<String, String>();
            ext.put("x-amz-acl", acl);

            String path = bucket + "/" + key + "?acl&versionId=" + version;
            return network.doRequest(path, "PUT", ext, null);
        }
        return "";
    }

    // 下载存储桶内的对象
    public String downloadObject(String bucket, String key) {
        String path = bucket + "/" + key;
        return network.doRequest(path, "GET", null, null);
    }

    // 大数据上传(步骤一)
    public String uploadBigObjectStep1(String bucket, String key) {
        String path = bucket + "/" + key + "?uploads";
        return network.doRequest(path, "POST", null, null);
    }

    // 大数据上传(步骤二)
    public String uploadBigObjectStep2(String bucket, String key, int step, String uploadId, RequestBody body) {
        String path = bucket + "/" + key + "?partNumber=" + Integer.toString(step) + "&uploadId=" + uploadId;
        Map<String, String> ext = new HashMap<String, String>();
        ext.put("content_type", "application/x-www-form-urlencoded");

        return network.doRequest(path, "PUT", ext, body);
    }

    //  大数据上传(步骤三)
    public String uploadBigObjectStep3(String bucket, String key, String uploadId, RequestBody body) {
        String path = bucket + "/" + key + "?uploadId=" + uploadId;

        try {
            String length = Long.toString(body.contentLength());
            Map<String, String> ext = new HashMap<String, String>();
            ext.put("content_type", "application/x-www-form-urlencoded");
            ext.put("content_length", length);

            return network.doRequest(path, "POST", ext, body);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }

    }

}
