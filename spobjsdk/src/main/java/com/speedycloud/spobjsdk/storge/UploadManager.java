package com.speedycloud.spobjsdk.storge;

import android.util.Log;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.speedycloud.spobjsdk.R;
import com.speedycloud.spobjsdk.collect.Config;
import com.speedycloud.spobjsdk.collect.UploadInfoCollector;
import com.speedycloud.spobjsdk.http.Client;
import com.speedycloud.spobjsdk.http.ResponseInfo;
import com.speedycloud.spobjsdk.utils.AsyncRun;
import com.speedycloud.spobjsdk.utils.StringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

import javax.crypto.KeyGenerator;

/**
 * Created by gyb on 2018/4/3.
 */


public final class UploadManager {
    private final Configuration config;
    private final Client client;
    public static final String strSpBucketVersionEnabled = "Enabled";
    public static final String strSpBucketVersionSuspended = "Suspended";

    public static final String strSpBucketAclPrivate = "private";
    public static final String strSpBucketAclPublicR = "public-read";
    public static final String strSpBucketAclPrivatRW = "public-read-write";

    private String strUploadId = "";

    public UploadManager() {
        this(new Configuration.Builder().build());
    }

    public UploadManager(Configuration config) {
        this.config = config;
        this.client = new Client(config.connectTimeout, config.responseTimeout,
                config.urlConverter);
    }

    public UploadManager(Recorder recorder, KeyGenerator keyGen) {
        this(new Configuration.Builder().recorder(recorder, keyGen).build());
    }

    public UploadManager(Recorder recorder) {
        this(recorder, null);
    }

    private static boolean areInvalidArg(final String key, byte[] data, File f, String token,
                                         final UpCompletionHandler complete) {
        if (complete == null) {
            throw new IllegalArgumentException("no UpCompletionHandler");
        }
        String message = null;
        if (f == null && data == null) {
            message = "no input data";
        } else if (token == null || token.equals("")) {
            message = "no token";
        }

        ResponseInfo info = null;
        if (message != null) {
            info = ResponseInfo.invalidArgument(message);
        } else if ((f != null && f.length() == 0) || (data != null && data.length == 0)) {
//            info = ResponseInfo.zeroSize(decodedToken);
        }

        if (info != null) {
            complete.complete(key, info, null);
            return true;
        }

        return false;
    }

    private static ResponseInfo areInvalidArg(final String key, byte[] data, File f, String token) {
        String message = null;
        if (f == null && data == null) {
            message = "no input data";
        } else if (token == null || token.equals("")) {
            message = "no token";
        }

        if (message != null) {
//            return ResponseInfo.invalidArgument(message, decodedToken);
        }
//
//        if (decodedToken == UpToken.NULL || decodedToken == null) {
//            return ResponseInfo.invalidToken("invalid token");
//        }

        if ((f != null && f.length() == 0) || (data != null && data.length == 0)) {
//            return ResponseInfo.zeroSize(decodedToken);
        }

        return null;
    }

    private static WarpHandler warpHandler(final UpCompletionHandler complete, final long size) {
        return new WarpHandler(complete, size);
    }

    /**
     * 上传数据
     *
     * @param data     上传的数据
     * @param key      上传数据保存的文件名
     * @param token    上传凭证
     * @param complete 上传完成后续处理动作
     * @param options  上传数据的可选参数
     */
    private void put(final byte[] data, final String key, final String token,
                    final UpCompletionHandler complete, final UploadOptions options) {
//        final UpToken decodedToken = UpToken.parse(token);
        if (areInvalidArg(key, data, null, token, complete)) {
            return;
        }

                FormUploader.upload(client, config, data, key, complete, options);

    }

    /**
     * 上传文件
     *
     * @param filePath          上传的文件路径
     * @param key               上传文件保存的文件名
     * @param token             上传凭证
     * @param completionHandler 上传完成的后续处理动作
     * @param options           上传数据的可选参数
     */
    private void put(String filePath, String bucket, String key, String uploadId, UpCompletionHandler completionHandler,
                    final UploadOptions options) {
        put(new File(filePath), bucket, key, uploadId, completionHandler, options);
    }

    /**
     * 上传文件
     *
     * @param file     上传的文件对象
     * @param key      上传文件保存的文件名
     * @param token    上传凭证
     * @param complete 上传完成的后续处理动作
     * @param options  上传数据的可选参数
     */
    private void put(final File file, final String bucket, final String key, final String uploadId, final UpCompletionHandler complete,
                    final UploadOptions options) {
        long time=System.currentTimeMillis();
        String recorderKey = bucket + "_" + key; // 桶_对象名字
        final WarpHandler completionHandler = warpHandler(complete, file != null ? file.length() : 0);
        ResumeUploader uploader = new ResumeUploader(client, config, file, bucket, key, uploadId, completionHandler, options, recorderKey);

        AsyncRun.runInMain(uploader);
    }

    /**
     * 同步上传文件。使用 form 表单方式上传，建议只在数据较小情况下使用此方式，如 file.size() < 1024 * 1024。
     *
     * @param data      上传的数据
     * @param key       上传数据保存的文件名
     * @param accessKey 上传用户的accessKey
     * @param
     * @param options   上传数据的可选参数
     * @return 响应信息 ResponseInfo#response 响应体，序列化后 json 格式
     */
    private ResponseInfo syncPut(byte[] data, String bucket,String key, String accessKey,String secretKey, UploadOptions options) {
//        final UpToken decodedToken = UpToken.parse(token);
        ResponseInfo info = areInvalidArg(key, data, null, null);
        if (info != null) {
            return info;
        }
        return FormUploader.syncUpload(client, config, data, null, bucket, key, options);
    }

    /**
     * 同步上传文件。使用 form 表单方式上传，建议只在文件较小情况下使用此方式，如 file.size() < 1024 * 1024。
     *
     * @param file    上传的文件对象
     * @param bucket  上传的桶
     * @param key     上传的对象
     * @param options 上传数据的可选参数
     * @return 响应信息 ResponseInfo#response 响应体，序列化后 json 格式
     */
    private ResponseInfo syncPut(File file,String bucket, String key, String accessKey, String secretKey, UploadOptions options) {
//        final UpToken decodedToken = UpToken.parse(token);
//        ResponseInfo info = areInvalidArg(key, null, file, token);
//        if (info != null) {
//            return info;
//        }
        return FormUploader.syncUpload(client, config, file, null,bucket, key, options);
    }

    /**
     * 同步上传文件。使用 form 表单方式上传，建议只在文件较小情况下使用此方式，如 file.size() < 1024 * 1024。
     *
     * @param file    上传的文件绝对路径
     * @param key     上传数据保存的文件名
     * @param token   上传凭证
     * @param options 上传数据的可选参数
     * @return 响应信息 ResponseInfo#response 响应体，序列化后 json 格式
     */
    private ResponseInfo syncPut(String file, String bucket, String key, String accessKey, String secretKey, UploadOptions options) {
        return syncPut(new File(file), bucket, key,accessKey,secretKey, options);
    }

    private static class WarpHandler implements UpCompletionHandler {
        final UpCompletionHandler complete;
        final long before = System.currentTimeMillis();
        final long size;

        WarpHandler(UpCompletionHandler complete, long size) {
            this.complete = complete;
            this.size = size;
        }

        @Override
        public void complete(final String key, final ResponseInfo res, final JSONObject response) {
            if (Config.isRecord) {
                final long after = System.currentTimeMillis();
                UploadInfoCollector.handleUpload(
                        // 延迟序列化.如果判断不记录,则不执行序列化
                        new UploadInfoCollector.RecordMsg() {

                            @Override
                            public String toRecordMsg() {
                                String[] ss = new String[]{res.statusCode + "", res.reqId, res.host, res.ip, res.port + "", (after - before) + "",
                                        res.timeStamp + "", size + "", "block", size + ""};
                                return StringUtils.join(ss, ",");
                            }
                        });
            }

            AsyncRun.runInMain(new Runnable() {
                @Override
                public void run() {
                    try {
                        complete.complete(key, res, response);
                    } catch (Throwable t) {
                        // do nothing
                        t.printStackTrace();
                    }
                }
            });
        }
    }

    /**
     * 创建桶
     * @param bucket
     * @param options
     * @return
     */
    public ResponseInfo createBucket(final String bucket,
                     UploadOptions options) {
        byte[] data = null;
        String exurl = bucket;
        if(options != null && options.params != null) {
            options.params.put("http_method", "PUT");
            options.params.put("url", exurl);
        }
        return FormUploader.syncUpload(client, config, data,exurl, bucket, null, options);
    }

    /**
     * 查询桶
     * @param bucket
     * @param options
     * @return
     */
    public ResponseInfo queryBucket(final String bucket,
                                     UploadOptions options) {
        byte[] data = null;
        String exurl = bucket + "?acl";
        if(options != null && options.params != null) {
            options.params.put("http_method", "GET");
            options.params.put("url", exurl);
        }
        return FormUploader.syncUpload(client, config, data,exurl, bucket, null, options);
    }

    /**
     * 设置桶版本
     * @param bucket
     * @param strVersion
     * @param options
     * @return
     */
    public ResponseInfo setBucketVersion(final String bucket,
                                    final String strVersion,
                                    UploadOptions options) {
        String strData = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<VersioningConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">\n<Status>" +
                strVersion + "</Status>\n</VersioningConfiguration>";


        byte[] data = null;
        try {
            data =  strData.getBytes("UTF-8");
        } catch (Exception e){
            return ResponseInfo.create(null, ResponseInfo.UnknownError, "", "","", "", "", "", "", 0, 0, 0,
                    e.getMessage(), data != null ? data.length : 0);
        }
        String exurl = bucket + "?versioning";
        if(options != null && options.params != null) {
            options.params.put("http_method", "PUT");
            options.params.put("url", exurl);
        }
        return FormUploader.syncUpload(client, config, data, exurl, bucket, null, options);
    }

    /**
     * 删除桶
     * @param bucket
     * @param options
     * @return
     */
    public ResponseInfo deleteBucket(final String bucket,
                                      UploadOptions options) {
        byte[] data = null;
        String exurl = bucket;
        if(options != null && options.params != null) {
            options.params.put("http_method", "DELETE");
            options.params.put("url", exurl);
        }
        return FormUploader.syncUpload(client, config, data,exurl, bucket, null, options);
    }

    /**
     * 修改桶权限
     * @param bucket
     * @param strUpdateAcl
     * @param options
     * @return
     */
    public ResponseInfo updateBucket(final String bucket, final String strUpdateAcl,
                                     UploadOptions options) {
        byte[] data = null;

        if(strUpdateAcl != strSpBucketAclPrivate &&
                strUpdateAcl != strSpBucketAclPrivatRW &&
                strUpdateAcl != strSpBucketAclPublicR){
            return ResponseInfo.create(null, ResponseInfo.UnknownError, "", "","", "", "", "", "", 0, 0, 0,
                   "", data != null ? data.length : 0);
        }

        String exurl = bucket + "?acl";
        if(options != null && options.params != null) {
            options.params.put("http_method", "PUT");
            options.params.put("url", exurl);
            options.params.put("x-amz-acl",strUpdateAcl);
        }
        return FormUploader.syncUpload(client, config, data, exurl, bucket, null, options);
    }


    /**
     * 查询桶版本
     * @param bucket
     * @param options
     * @return
     */
    public ResponseInfo queryBucketVersion(final String bucket,
                                     UploadOptions options) {
        byte[] data = null;
        String exurl = bucket + "?versioning";
        if(options != null && options.params != null) {
            options.params.put("http_method", "GET");
            options.params.put("url", exurl);
        }
        return FormUploader.syncUpload(client, config, data,exurl, bucket, null, options);
    }

    public void startUploadFile(final String bucket,
                                        final String obj,
                                        final String filePath,
                                        UploadOptions options){
        String havedUploadId = checkExistUploadId(bucket,obj);
        if(havedUploadId != null ){
            upload(havedUploadId,filePath,bucket, obj, options);
        }else {
            // 第一步获取uploadId
            ResponseInfo res = null;
            byte[] data = null;
            String exurl = bucket + "/" + obj + "?uploads";
            if (options != null && options.params != null) {
                options.params.put("http_method", "POST");
                options.params.put("url", exurl);
            }

            res = FormUploader.syncUpload(client, config, data, exurl, bucket, null, options);

            try {
                analysisJson(res.response);
                if (strUploadId.length() > 0)
                    upload(strUploadId, filePath, bucket, obj, options);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void upload(String uploadId,String strFilePath,String bucket, String obj, UploadOptions uploadOptions) {
        File uploadFile = new File(strFilePath);
        String uploadFileKey = obj;
        final long startTime = System.currentTimeMillis();
        final long fileLength = uploadFile.length();
//        this.uploadFileLength = fileLength;
//        this.uploadLastTimePoint = startTime;
//        this.uploadLastOffset = 0;

//        AsyncRun.runInMain(new Runnable() {
//            @Override
//            public void run() {
//                // prepare status
//                uploadPercentageTextView.setText("0 %");
//                uploadSpeedTextView.setText("0 KB/s");
//                uploadFileLengthTextView.setText(Tools.formatSize(fileLength));
//                uploadStatusLayout.setVisibility(LinearLayout.VISIBLE);
//            }
//        });
//        writeLog(context.getString(R.string.qiniu_upload_file) + "...");
        put(uploadFile, bucket, uploadFileKey,uploadId,
                new UpCompletionHandler() {
                    @Override
                    public void complete(String key, ResponseInfo respInfo,
                                         JSONObject jsonData) {
//                        AsyncRun.runInMain(new Runnable() {
//                            @Override
//                            public void run() {
//                                // reset status
//                                uploadStatusLayout
//                                        .setVisibility(LinearLayout.INVISIBLE);
//                                uploadProgressBar.setProgress(0);
//                            }
//                        });
                        long lastMillis = System.currentTimeMillis()
                                - startTime;
                        if (respInfo.isOK()) {
                            try {
                                String fileKey = jsonData.getString("key");
                                String fileHash = jsonData.getString("hash");
//                                writeLog("File Size: "
//                                        + Tools.formatSize(uploadFileLength));
//                                writeLog("File Key: " + fileKey);
//                                writeLog("File Hash: " + fileHash);
//                                writeLog("Last Time: "
//                                        + Tools.formatMilliSeconds(lastMillis));
//                                writeLog("Average Speed: "
//                                        + Tools.formatSpeed(fileLength,
//                                        lastMillis));
//                                writeLog("X-Reqid: " + respInfo.reqId);
//                                writeLog("X-Via: " + respInfo.xvia);
//                                writeLog("--------------------------------");
                            } catch (JSONException e) {
//                                AsyncRun.runInMain(new Runnable() {
//                                    @Override
//                                    public void run() {
//                                        Toast.makeText(
//                                                context,
//                                                context.getString(R.string.qiniu_upload_file_response_parse_error),
//                                                Toast.LENGTH_LONG).show();
//                                    }
//                                });
//
//                                writeLog(context
//                                        .getString(R.string.qiniu_upload_file_response_parse_error));
//                                if (jsonData != null) {
//                                    writeLog(jsonData.toString());
//                                }
//                                writeLog("--------------------------------");
                            }
                        } else {
//                            AsyncRun.runInMain(new Runnable() {
//                                @Override
//                                public void run() {
//                                    Toast.makeText(
//                                            context,
//                                            context.getString(R.string.qiniu_upload_file_failed),
//                                            Toast.LENGTH_LONG).show();
//                                }
//                            });
//
//                            writeLog(respInfo.toString());
//                            if (jsonData != null) {
//                                writeLog(jsonData.toString());
//                            }
//                            writeLog("--------------------------------");
                        }
                    }

                }, uploadOptions);
    }

    private String checkExistUploadId(final String bucket,final String obj){
        String uploadId = null;
        if (config.recorder == null) {
            return null;
        }
        byte[] data = config.recorder.get(bucket + "_" + obj);
        if (data == null) {
            return null;
        }
        String jsonStr = new String(data);
        JSONObject objJson;
        try {
            objJson = new JSONObject(jsonStr);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
        return objJson.optString("uploadId", null);
    }
    @SuppressWarnings("rawtypes")
    public void analysisJson(Object objJson) throws JSONException {
        //如果obj为json数组
        if(objJson instanceof JSONArray){
            JSONArray objArray = (JSONArray)objJson;
            for (int i = 0; i < objArray.length(); i++) {
                analysisJson(objArray.get(i));
            }
        }
        //如果为json对象
        if(objJson instanceof JSONObject){
            JSONObject jsonObject = (JSONObject)objJson;
            Iterator it = jsonObject.keys();
            while(it.hasNext()){
                String key = it.next().toString();
                Object object = jsonObject.get(key);
                //如果得到的是数组
                if(object instanceof JSONArray){
                    JSONArray objArray = (JSONArray)object;
                    analysisJson(objArray);
                }
                //如果key中是一个json对象
                else if(object instanceof JSONObject){
                    analysisJson((JSONObject)object);
                }
                //如果key中是其他
                else{
                    if (key.equals("UploadId")) {
                        System.out.println("[" + key + "]:" + object.toString() + " ");
                        strUploadId = object.toString().trim();
                    }
                }
            }
        }
    }

    /**
     * 删除桶内对象
     * @param bucket
     * @param obj
     * @param options
     * @return
     */
    public ResponseInfo deleteBucketObj(final String bucket,
                                     final String obj,
                                     UploadOptions options) {
        byte[] data = null;
        String exurl = bucket + "/" + obj;
        if(options != null && options.params != null) {
            options.params.put("http_method", "DELETE");
            options.params.put("url", exurl);
        }
        return FormUploader.syncUpload(client, config, data,exurl, bucket, obj, options);
    }

    /**
     * 删除桶内指定版本对象
     * @param bucket
     * @param obj
     * @param versionId
     * @param options
     * @return
     */
    public ResponseInfo deleteBucketAssignObj(final String bucket,
                                        final String obj,
                                        final String versionId,
                                        UploadOptions options) {
        byte[] data = null;
        String exurl = bucket + "/" + obj + "?versionId=" + versionId;
        if(options != null && options.params != null) {
            options.params.put("http_method", "DELETE");
            options.params.put("url", exurl);
        }
        return FormUploader.syncUpload(client, config, data,exurl, bucket, obj, options);
    }

    /**
     * 修改桶内对象权限
     * @param bucket
     * @param obj
     * @param strUpdateAcl
     * @param options
     * @return
     */
    public ResponseInfo updateBucketObj(final String bucket,final String obj, final String strUpdateAcl,
                                         UploadOptions options) {
        byte[] data = null;

        if(strUpdateAcl != strSpBucketAclPrivate &&
                strUpdateAcl != strSpBucketAclPrivatRW &&
                strUpdateAcl != strSpBucketAclPublicR){
            return ResponseInfo.create(null, ResponseInfo.UnknownError, "", "","", "", "", "", "", 0, 0, 0,
                    "", data != null ? data.length : 0);
        }

        String exurl = bucket + "/" + obj + "?acl";
        if(options != null && options.params != null) {
            options.params.put("http_method", "PUT");
            options.params.put("url", exurl);
            options.params.put("x-amz-acl",strUpdateAcl);
        }
        return FormUploader.syncUpload(client, config, data, exurl, bucket, obj, options);
    }

    /**
     * 修改指定对象权限
     * @param bucket
     * @param obj
     * @param versionId
     * @param strUpdateAcl
     * @param options
     * @return
     */
    public ResponseInfo updateBucketAssignObj(final String bucket,final String obj, final String versionId, final String strUpdateAcl,
                                        UploadOptions options) {
        byte[] data = null;

        if(strUpdateAcl != strSpBucketAclPrivate &&
                strUpdateAcl != strSpBucketAclPrivatRW &&
                strUpdateAcl != strSpBucketAclPublicR){
            return ResponseInfo.create(null, ResponseInfo.UnknownError, "", "","", "", "", "", "", 0, 0, 0,
                    "", data != null ? data.length : 0);
        }

        String exurl = bucket + "/" + obj + "?acl" + "&versionId=" + versionId;
        if(options != null && options.params != null) {
            options.params.put("http_method", "PUT");
            options.params.put("url", exurl);
            options.params.put("x-amz-acl",strUpdateAcl);
        }
        return FormUploader.syncUpload(client, config, data, exurl, bucket, obj, options);
    }

    /**
     * 查询对象权限
     * @param bucket
     * @param obj
     * @param options
     * @return
     */
    public ResponseInfo queryBucketObjAcl(final String bucket,final String obj,
                                              UploadOptions options) {
        byte[] data = null;

        String exurl = bucket + "/" + obj;
        if(options != null && options.params != null) {
            options.params.put("http_method", "GET");
            options.params.put("url", exurl);
        }
        return FormUploader.syncUpload(client, config, data, exurl, bucket, obj, options);
    }


    /**
     * 查询桶内所有对象版本信息
     * @param bucket
     * @param options
     * @return
     */
    public ResponseInfo queryBucketAllObjVersion(final String bucket,
                                          UploadOptions options) {
        byte[] data = null;

        String exurl = bucket + "?versions";
        if(options != null && options.params != null) {
            options.params.put("http_method", "GET");
            options.params.put("url", exurl);
        }
        return FormUploader.syncUpload(client, config, data, exurl, bucket, null, options);
    }

    /**
     * 查询桶内所有对象
     * @param bucket
     * @param options
     * @return
     */
    public ResponseInfo queryBucketAllObj(final String bucket,
                                                 UploadOptions options) {
        byte[] data = null;

        String exurl = bucket;
        if(options != null && options.params != null) {
            options.params.put("http_method", "GET");
            options.params.put("url", exurl);
        }
        return FormUploader.syncUpload(client, config, data, exurl, bucket, null, options);
    }
}
