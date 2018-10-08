package com.speedycloud.spobjsdk.storge;

import android.util.Log;

import com.speedycloud.spobjsdk.http.Client;
import com.speedycloud.spobjsdk.http.PostArgs;
import com.speedycloud.spobjsdk.http.ProgressHandler;
import com.speedycloud.spobjsdk.http.ResponseInfo;
import com.speedycloud.spobjsdk.utils.AndroidNetwork;
import com.speedycloud.spobjsdk.utils.StringMap;

import java.io.File;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Created by gyb on 2018/4/3.
 */

public class FormUploader {
    /**
     * 上传数据，并以指定的key保存文件
     *
     * @param httpManager       HTTP连接管理器
     * @param data              上传的数据
     * @param key               上传的数据保存的文件名
     * @param token             上传凭证
     * @param completionHandler 上传完成后续处理动作
     * @param options           上传时的可选参数
     */
    static void upload(Client httpManager, Configuration config, byte[] data, String key, final UpCompletionHandler completionHandler,
                       final UploadOptions options) {
        post(data, null, key, completionHandler, options, httpManager, config);
    }

    /**
     * 上传文件，并以指定的key保存文件
     *
     * @param client            HTTP连接管理器
     * @param file              上传的文件
     * @param key               上传的数据保存的文件名
     * @param token             上传凭证
     * @param completionHandler 上传完成后续处理动作
     * @param options           上传时的可选参数
     */
    static void upload(Client client, Configuration config, File file, String key,
                       UpCompletionHandler completionHandler, UploadOptions options) {
        post(null, file, key, completionHandler, options, client, config);
    }

    private static void post(byte[] data, File file, String k,
                             final UpCompletionHandler completionHandler,
                             final UploadOptions optionsIn, final Client client, final Configuration config) {
        final String key = k;
        StringMap params = new StringMap();
        final PostArgs args = new PostArgs();
        if (k != null) {
            params.put("key", key);
            args.fileName = key;
        } else {
            args.fileName = "?";
        }

        // data is null , or file is null
        if (file != null) {
            args.fileName = file.getName();
        }

//        params.put("token", token.token);

        final UploadOptions options = optionsIn != null ? optionsIn : UploadOptions.defaultOptions();
        params.putFileds(options.params);

        long crc = 0;
//        if (file != null) {
//            try {
//                crc = Crc32.file(file);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        } else {
//            crc = Crc32.bytes(data);
//        }
        params.put("crc32", "" + crc);

        final ProgressHandler progress = new ProgressHandler() {
            @Override
            public void onProgress(long bytesWritten, long totalSize) {
                double percent = (double) bytesWritten / (double) totalSize;
                if (percent > 0.95) {
                    percent = 0.95;
                }
                options.progressHandler.progress(key,totalSize, percent);
            }
        };

        args.data = data;
        args.file = file;
        args.mimeType = options.mimeType;
        args.params = params;

        final String upHost = "";//config.zone.upHost(token.token, config.useHttps, null);
        Log.d("Qiniu.FormUploader", "upload use up host " + upHost);
//        CompletionHandler completion = new CompletionHandler() {
//            @Override
//            public void complete(ResponseInfo info, JSONObject response) {
//                if (info.isNetworkBroken() && !AndroidNetwork.isNetWorkReady()) {
//                    options.netReadyHandler.waitReady();
//                    if (!AndroidNetwork.isNetWorkReady()) {
//                        completionHandler.complete(key, info, response);
//                        return;
//                    }
//                }
//
//                if (info.isOK()) {
//                    options.progressHandler.progress(key, 1.0);
//                    completionHandler.complete(key, info, response);
//                } else if (info.needRetry()) {
//                    final String upHostRetry = config.zone.upHost(token.token, config.useHttps, upHost);
//                    Log.d("Qiniu.FormUploader", "retry upload first time use up host " + upHostRetry);
//                    CompletionHandler retried = new CompletionHandler() {
//                        @Override
//                        public void complete(ResponseInfo info, JSONObject response) {
//                            if (info.isOK()) {
//                                options.progressHandler.progress(key, 1.0);
//                                completionHandler.complete(key, info, response);
//                            } else if (info.needRetry()) {
//                                final String upHostRetry2 = config.zone.upHost(token.token, config.useHttps, upHostRetry);
//                                Log.d("Qiniu.FormUploader", "retry upload second time use up host " + upHostRetry2);
//                                CompletionHandler retried2 = new CompletionHandler() {
//                                    @Override
//                                    public void complete(ResponseInfo info2, JSONObject response2) {
//                                        if (info2.isOK()) {
//                                            options.progressHandler.progress(key, 1.0);
//                                        } else if (info2.needRetry()) {
//                                            config.zone.frozenDomain(upHostRetry2);
//                                        }
//                                        completionHandler.complete(key, info2, response2);
//                                    }
//                                };
//                                client.asyncMultipartPost(upHostRetry2, args, token, progress, retried2, options.cancellationSignal);
//                            } else {
//                                completionHandler.complete(key, info, response);
//                            }
//                        }
//                    };
//                    client.asyncMultipartPost(upHostRetry, args, token, progress, retried, options.cancellationSignal);
//                } else {
//                    completionHandler.complete(key, info, response);
//                }
//            }
//        };

//        client.asyncMultipartPost(upHost, args, progress, completion, options.cancellationSignal);
    }

    /**
     * 上传数据，并以指定的key保存文件
     *
     * @param client  HTTP连接管理器
     * @param data    上传的数据
     * @param key     上传的数据保存的文件名
     * @param options 上传时的可选参数
     * @return 响应信息 ResponseInfo#response 响应体，序列化后 json 格式
     */
    public static ResponseInfo syncUpload(Client client, Configuration config, byte[] data, String exurl, String bucket, String key, UploadOptions options) {
        try {
            return syncUpload0(client, config, data, null, exurl, bucket, key, options);
        } catch (Exception e) {

            return ResponseInfo.create(null, ResponseInfo.UnknownError, "", "","", "", "", "", "", 0, 0, 0,
                    e.getMessage(), data != null ? data.length : 0);
        }
    }

    /**
     * 上传文件，并以指定的key保存文件
     *
     * @param client  HTTP连接管理器
     * @param file    上传的文件
     * @param key     上传的数据保存的文件名
     * @param token   上传凭证
     * @param options 上传时的可选参数
     * @return 响应信息 ResponseInfo#response 响应体，序列化后 json 格式
     */
    public static ResponseInfo syncUpload(Client client, Configuration config, File file, String exurl, String bucket, String key, UploadOptions options) {
        try {
            return syncUpload0(client, config, null, file, exurl, bucket, key, options);
        } catch (Exception e) {
            return ResponseInfo.create(null, ResponseInfo.UnknownError, "", "","", "", "", "", "", 0, 0, 0,
                    e.getMessage(), file != null ? file.length() : 0);
        }
    }

    private static ResponseInfo syncUpload0(Client client, Configuration config, byte[] data, File file, String exurl, String bucket,
                                            String key, UploadOptions optionsIn) {
        StringMap params = new StringMap();
        final PostArgs args = new PostArgs();
        if (key != null) {
            params.put("key", key);
            args.fileName = key;
        } else {
            args.fileName = "?";
        }

        // data is null , or file is null
        if (file != null) {
            args.fileName = file.getName();
        }
        final UploadOptions options = optionsIn != null ? optionsIn : UploadOptions.defaultOptions();
        // 增加签名
        options.addExtensParams(options.params);
        params.putFileds(options.params);

        RequestBody body = null;

        final Request.Builder builder = new Request.Builder();
        String url = options.baseServer + "/" + exurl;
        boolean isJson = true;
        if (url.contains("?") && isJson) {
            url += "&ctype=json";
        }
        if (!url.contains("?") && isJson) {
            url += "?ctype=json";
        }
        builder.url(url);

        params.forEach(new StringMap.Consumer() {
            @Override
            public void accept(String key, Object value) {
                builder.addHeader(key, value.toString());
            }
        });

//        MediaType type = MediaType.parse("");
//        RequestBody data1 = RequestBody.create(type, "");
//        builder.method(builder.build().header("http_method"), data1);
//
//        ResponseInfo info = client.syncSend(builder, params, 0);

        args.data = data;
        args.file = file;
        args.mimeType = options.mimeType;
        args.params = params;

        ResponseInfo info = null;

        if(params.get("http_method").equals("POST")){
            if(args.data == null && args.file == null) {
                args.data = new byte[]{};
            }
            info = client.syncMultipartPost(url, args);
        }else if(params.get("http_method").equals("PUT")){
            if(args.data == null && args.file == null) {
                args.data = new byte[]{};
            }
            info = client.syncMultipartPut(url, args);
        }else if(params.get("http_method").equals("GET")){
            info = client.syncGet(url,params);
        }else if(params.get("http_method").equals("DELETE")){
            info = client.syncDelete(url,params);
        }



        if (info.isOK()) {
            return info;
        }

        //retry for the first time
        if (info.needRetry()) {
            if (info.isNetworkBroken() && !AndroidNetwork.isNetWorkReady()) {
                options.netReadyHandler.waitReady();
                if (!AndroidNetwork.isNetWorkReady()) {
                    return info;
                }
            }

            //retry for the second time
//            String upHostRetry = config.zone.upHost(token.token, config.useHttps, upHost);
//            Log.d("Qiniu.FormUploader", "sync upload retry first time use up host " + upHostRetry);
//            info = client.syncMultipartPost(upHostRetry, args, token);

            if (info.needRetry()) {
                if (info.isNetworkBroken() && !AndroidNetwork.isNetWorkReady()) {
                    options.netReadyHandler.waitReady();
                    if (!AndroidNetwork.isNetWorkReady()) {
                        return info;
                    }
                }

//                String upHostRetry2 = config.zone.upHost(token.token, config.useHttps, upHostRetry);
//                Log.d("Qiniu.FormUploader", "sync upload retry second time use up host " + upHostRetry2);
//                info = client.syncMultipartPost(upHostRetry2, args, token);
//                if (info.needRetry()) {
//                    config.zone.frozenDomain(upHostRetry2);
//                }
            }
        }

        return info;
    }
}
