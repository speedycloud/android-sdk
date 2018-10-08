package com.speedycloud.spobjsdk.storge;

import android.util.Log;

import com.speedycloud.spobjsdk.http.Client;
import com.speedycloud.spobjsdk.http.CompletionHandler;
import com.speedycloud.spobjsdk.http.ProgressHandler;
import com.speedycloud.spobjsdk.http.ResponseInfo;
import com.speedycloud.spobjsdk.utils.AndroidNetwork;
import com.speedycloud.spobjsdk.utils.StringMap;
import com.speedycloud.spobjsdk.utils.StringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.speedycloud.spobjsdk.http.Client.FormMime;
import static com.speedycloud.spobjsdk.storge.Configuration.BLOCK_SIZE;
import static java.lang.String.format;

/**
 * Created by gyb on 2018/4/3.
 */

final class ResumeUploader implements Runnable {

    private final long totalSize;
    private final String key;
    private final String bucket;
    private final String uploadId;
    private final UpCompletionHandler completionHandler;
    private final UploadOptions options;
    private final Client client;
    private final Configuration config;
    private final byte[] chunkBuffer;
    private final String[] contexts;
//        private final Header[] headers;
    private final StringMap headers;
    private final long modifyTime;
    private final String recorderKey;
    private RandomAccessFile file;
    private File f;
    private long crc32;

    ResumeUploader(Client client, Configuration config, File f, String bucket, String key, String uploadId,
                   final UpCompletionHandler completionHandler, UploadOptions options, String recorderKey) {
        this.client = client;
        this.config = config;
        this.f = f;
        this.recorderKey = recorderKey;
        this.totalSize = f.length();
        this.key = key;
        this.bucket = bucket;
        this.uploadId = uploadId;
        this.headers = new StringMap();
//        this.headers = new StringMap().put("Authorization", "UpToken " + token.token);
        this.file = null;
        this.completionHandler = new UpCompletionHandler() {
            @Override
            public void complete(String key, ResponseInfo info, JSONObject response) {
                if (file != null) {
                    try {
                        file.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                completionHandler.complete(key, info, response);
            }
        };
        this.options = options != null ? options : UploadOptions.defaultOptions();
        chunkBuffer = new byte[config.chunkSize];
        long count = (totalSize + BLOCK_SIZE - 1) / BLOCK_SIZE;
        contexts = new String[(int) count];
        modifyTime = f.lastModified();
    }

    private static boolean isChunkOK(ResponseInfo info, JSONObject response) {
        return info.statusCode == 200;
//        return info.statusCode == 200 && info.error == null && (info.hasReqId() || isChunkResOK(response));
    }

    private static boolean isChunkResOK(JSONObject response) {
        try {
            // getXxxx 若获取不到值,会抛出异常
            response.getString("ctx");
            response.getLong("crc32");
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private static boolean isNotChunkToQiniu(ResponseInfo info, JSONObject response) {
        return info.statusCode < 500 && info.statusCode > 200 ;
    }

    public void run() {
        long offset = recoveryFromRecord();
        try {
            file = new RandomAccessFile(f, "r");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            completionHandler.complete(key, ResponseInfo.fileError(e), null);
            return;
        }

        nextTask(offset, 0, uploadId);
    }

    /**
     * 创建块，并上传第一个分片内容
     *
     * @param upHost             上传主机
     * @param offset             本地文件偏移量
     * @param blockSize          分块的块大小
     * @param chunkSize          分片的片大小
     * @param progress           上传进度
     * @param _completionHandler 上传完成处理动作
     */
    private void makeBlock(String upHost, long offset, int blockSize, int chunkSize, ProgressHandler progress,
                           CompletionHandler _completionHandler, UpCancellationSignal c) {
        Log.d("gyb","offset = " + offset +  " blockSize = " + blockSize);
        long index = (int)offset / BLOCK_SIZE;
        String path = format(Locale.ENGLISH, "%s/%s?partNumber=%d&uploadId=%s", bucket,key,index + 1,uploadId);
        Map<String,String> params = new HashMap<String, String>();
        params.put("http_method","PUT");
        params.put("url",path);

        for (String key : params.keySet()) {
            Log.d("gyb","params key=" + key + " value = " + params.get(key));
        }

        headers.putFileds(options.generateHeaders(params,true));
        try {
            file.seek(offset);
            file.read(chunkBuffer, 0, chunkSize);
        } catch (IOException e) {
//            completionHandler.complete(key, ResponseInfo.fileError(e, token), null);
            return;
        }
//        this.crc32 = Crc32.bytes(chunkBuffer, 0, chunkSize);
        String postUrl = format("%s/%s", upHost, path);
        boolean isJson = true;
        if (postUrl.contains("?") && isJson) {
            postUrl += "&ctype=json";
        }
        if (!postUrl.contains("?") && isJson) {
            postUrl += "?ctype=json";
        }
        Log.d("gyb","putUrl = " + postUrl);
        put(postUrl, chunkBuffer, 0, chunkSize, progress, _completionHandler, c);
    }

    private void putChunk(String upHost, long offset, int chunkSize, String context, ProgressHandler progress,
                          CompletionHandler _completionHandler, UpCancellationSignal c) {
        int chunkOffset = (int) (offset % BLOCK_SIZE);
        String path = format(Locale.ENGLISH, "/bput/%s/%d", context, chunkOffset);
        try {
            file.seek(offset);
            file.read(chunkBuffer, 0, chunkSize);
        } catch (IOException e) {
//            completionHandler.complete(key, ResponseInfo.fileError(e, token), null);
            return;
        }
//        this.crc32 = Crc32.bytes(chunkBuffer, 0, chunkSize);

        String postUrl = format("%s%s", upHost, path);
        post(postUrl, chunkBuffer, 0, chunkSize, progress, _completionHandler, c);
    }

    private void makeFile(String upHost, CompletionHandler _completionHandler, UpCancellationSignal c) {
        Log.d("gyb","last post ....");
        String bodyStr = format(Locale.ENGLISH,"%s","<CompleteMultipartUpload>\n");
        for (int i = 0; i < contexts.length; i++) {
            String strPart = format(Locale.ENGLISH,"%s%d%s%s%s",
                     "<Part>\n<PartNumber>",i+1,"</PartNumber>\n<ETag>",contexts[i],"</ETag>\n</Part>\n");
            bodyStr = format(Locale.ENGLISH,"%s%s",bodyStr ,strPart);
        }
        bodyStr = format(Locale.ENGLISH,"%s%s",bodyStr,"</CompleteMultipartUpload>");
        Log.d("gyb",bodyStr);

        byte[] data = null;
        try {
            data = bodyStr.getBytes();
        } catch (Exception e){
            e.printStackTrace();
        }

        String exUrl = format(Locale.ENGLISH,"%s/%s?uploadId=%s",bucket, key,uploadId);
        String postUrl = format(Locale.ENGLISH,"%s/%s",upHost , exUrl);

        Map<String,String> params = new HashMap<String, String>();
        params.put("http_method","POST");
        params.put("url",exUrl);
        params.put("Content_Type",FormMime);
        params.put("Content-Length",Long.toString(data.length));

        boolean isJson = true;
        if (postUrl.contains("?") && isJson) {
            postUrl += "&ctype=json";
        }
        if (!postUrl.contains("?") && isJson) {
            postUrl += "?ctype=json";
        }

        for (String key : params.keySet()) {
            Log.d("gyb","params key=" + key + " value = " + params.get(key));
        }

        headers.putFileds(options.generateHeaders(params,true));
        Log.d("gyb","postUrl = " + postUrl);
        post(postUrl, data, 0, data.length, null, _completionHandler, c);
    }

    private void post(String upHost, byte[] data, int offset, int dataSize, ProgressHandler progress,
                      CompletionHandler completion, UpCancellationSignal c) {
        client.asyncPost(upHost, data, offset, dataSize, headers, totalSize, progress, completion, c);
    }

    private void put(String upHost, byte[] data, int offset, int dataSize, ProgressHandler progress,
                     CompletionHandler completion, UpCancellationSignal c){
        client.asyncPut(upHost, data, offset, dataSize, headers, totalSize, progress, completion, c);
    }

    private long calcPutSize(long offset) {
        long left = totalSize - offset;
        return left < config.chunkSize ? left : config.chunkSize;
    }

    private long calcBlockSize(long offset) {
        long left = totalSize - offset;
        return left < BLOCK_SIZE ? left : BLOCK_SIZE;
    }

    private boolean isCancelled() {
        return options.cancellationSignal.isCancelled();
    }

    private void nextTask(final long offset, final int retried, final String uploadId) {
        if (isCancelled()) {
            ResponseInfo i = ResponseInfo.cancelled();
            completionHandler.complete(key, i, null);
            return;
        }

        if (offset == totalSize) {
            //完成操作,返回的内容不确定,是否真正成功逻辑让用户自己判断
            CompletionHandler complete = new CompletionHandler() {
                @Override
                public void complete(ResponseInfo info, JSONObject response) {
                    if (info.isNetworkBroken() && !AndroidNetwork.isNetWorkReady()) {
                        options.netReadyHandler.waitReady();
                        if (!AndroidNetwork.isNetWorkReady()) {
                            completionHandler.complete(key, info, response);
                            return;
                        }
                    }

                    if (info.isOK()) {
                        removeRecord();
                        options.progressHandler.progress(key, totalSize,1.0);
                        completionHandler.complete(key, info, response);
                        return;
                    }

                    // mkfile  ，允许多重试一次
                    if (info.needRetry() && retried < config.retryMax + 1) {
                        nextTask(offset, retried + 1, options.baseServer);
                        return;
                    }
                    completionHandler.complete(key, info, response);
                }
            };
            makeFile(options.baseServer, complete, options.cancellationSignal);
            return;
        }

        final int chunkSize = (int) calcPutSize(offset);
        ProgressHandler progress = new ProgressHandler() {
            @Override
            public void onProgress(long bytesWritten, long iTotalSize) {
                double percent = (double) (offset + bytesWritten) / iTotalSize;
                if (percent > 0.95) {
                    percent = 0.95;
                }
                options.progressHandler.progress(key, iTotalSize, percent);
            }
        };

        // 分片上传
        CompletionHandler complete = new CompletionHandler() {
            @Override
            public void complete(ResponseInfo info, JSONObject response) {
                if (info.isNetworkBroken() && !AndroidNetwork.isNetWorkReady()) {
                    options.netReadyHandler.waitReady();
                    if (!AndroidNetwork.isNetWorkReady()) {
                        completionHandler.complete(key, info, response);
                        return;
                    }
                }

                if (info.isCancelled()) {
                    completionHandler.complete(key, info, response);
                    return;
                }


                if (!isChunkOK(info, response)) {
//                    String upHostRetry = config.zone.upHost(token.token, config.useHttps, upHost);
                    if (info.statusCode == 701 && retried < config.retryMax) {
//                        nextTask((offset / Configuration.BLOCK_SIZE) * Configuration.BLOCK_SIZE, retried + 1, upHost);
                        return;
                    }

                    if ( ((isNotChunkToQiniu(info, response) || info.needRetry())
                            && retried < config.retryMax)) {
                        nextTask(offset, retried + 1, options.baseServer);
                        return;
                    }

                    completionHandler.complete(key, info, response);
                    return;
                }else{
                    // 200
                    contexts[(int) (offset / BLOCK_SIZE)] = info.strEtag;
                    Log.d("gyb","index = " + (int) (offset / BLOCK_SIZE) + " Etag = " + info.strEtag);
                    record(offset + chunkSize);
                    nextTask(offset + chunkSize, retried, options.baseServer);
                }
            }
        };

        if (offset % BLOCK_SIZE == 0) {
            int blockSize = (int) calcBlockSize(offset);
            makeBlock(options.baseServer, offset, blockSize, blockSize, progress, complete, options.cancellationSignal);
            return;
        }
    }

    private long recoveryFromRecord() {
        if (config.recorder == null) {
            return 0;
        }
        byte[] data = config.recorder.get(recorderKey);
        if (data == null) {
            return 0;
        }
        String jsonStr = new String(data);
        JSONObject obj;
        try {
            obj = new JSONObject(jsonStr);
        } catch (JSONException e) {
            e.printStackTrace();
            return 0;
        }
        long offset = obj.optLong("offset", 0);
        long modify = obj.optLong("modify_time", 0);
        long fSize = obj.optLong("size", 0);
        String uploadId = obj.optString("uploadId",null);
        JSONArray array = obj.optJSONArray("contexts");
        if (offset == 0 || modify != modifyTime || fSize != totalSize || array == null || array.length() == 0 || uploadId.length() == 0) {
            return 0;
        }
        for (int i = 0; i < array.length(); i++) {
            contexts[i] = array.optString(i);
        }

        return offset;
    }

    private void removeRecord() {
        if (config.recorder != null) {
            config.recorder.del(recorderKey);
        }
    }

    // save json value
    //{
    //    "size":filesize,
    //    "offset":lastSuccessOffset,
    //    "modify_time": lastFileModifyTime,
    //    "contexts": contexts
    //}
    private void record(long offset) {
        if (config.recorder == null || offset == 0) {
            return;
        }
        String data = format(Locale.ENGLISH, "{\"uploadId\":%s, \"size\":%d,\"offset\":%d, \"modify_time\":%d, \"contexts\":[%s]}",
                uploadId,totalSize, offset, modifyTime, StringUtils.jsonJoin(contexts));
        config.recorder.set(recorderKey, data.getBytes());
    }


    private URI newURI(URI uri, String path) {
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), path, null, null);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return uri;
    }
}
