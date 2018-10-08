package com.speedycloud.spobjsdk.http;

import android.util.Log;

import com.speedycloud.spobjsdk.common.Constants;
import com.speedycloud.spobjsdk.storge.UpCancellationSignal;
import com.speedycloud.spobjsdk.utils.AsyncRun;
import com.speedycloud.spobjsdk.utils.StringMap;
import com.speedycloud.spobjsdk.utils.StringUtils;

import org.json.JSONObject;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import static com.speedycloud.spobjsdk.http.ResponseInfo.NetworkError;

/**
 * Created by gyb on 2018/4/3.
 */

public final class Client {
    public static final String ContentTypeHeader = "Content-Type";
    public static final String DefaultMime = "application/octet-stream";
    public static final String JsonMime = "application/json";
    public static final String FormMime = "application/x-www-form-urlencoded";
    public static final String XmlMime = "application/xml"; //sp 默认回复
    private final UrlConverter converter;
    private OkHttpClient httpClient;

    public Client() {
        this(10, 30, null);
    }

    public Client( int connectTimeout, int responseTimeout, UrlConverter converter) {
        this.converter = converter;
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

//        if (proxy != null) {
//            builder.proxy(proxy.proxy());
//            if (proxy.user != null && proxy.password != null) {
//                builder.proxyAuthenticator(proxy.authenticator());
//            }
//        }
//        if (dns != null) {
//            builder.dns(new Dns() {
//                @Override
//                public List<InetAddress> lookup(String hostname) throws UnknownHostException {
//                    InetAddress[] ips;
//                    try {
//                        ips = dns.queryInetAdress(new Domain(hostname));
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                        throw new UnknownHostException(e.getMessage());
//                    }
//                    if (ips == null) {
//                        throw new UnknownHostException(hostname + " resolve failed");
//                    }
//                    List<InetAddress> l = new ArrayList<>();
//                    Collections.addAll(l, ips);
//                    return l;
//                }
//            });
//        }
        builder.networkInterceptors().add(new Interceptor() {
            @Override
            public okhttp3.Response intercept(Chain chain) throws IOException {
                Request request = chain.request();
                final long before = System.currentTimeMillis();
                okhttp3.Response response = chain.proceed(request);
                final long after = System.currentTimeMillis();

                ResponseTag tag = (ResponseTag) request.tag();
                String ip = "";
                try {
                    ip = chain.connection().socket().getRemoteSocketAddress().toString();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                tag.ip = ip;
                tag.duration = after - before;
                return response;
            }
        });

        builder.connectTimeout(connectTimeout, TimeUnit.SECONDS);
        builder.readTimeout(responseTimeout, TimeUnit.SECONDS);
        builder.writeTimeout(0, TimeUnit.SECONDS);
        httpClient = builder.build();

    }

    private static String via(okhttp3.Response response) {
        String via;
        if (!(via = response.header("X-Via", "")).equals("")) {
            return via;
        }

        if (!(via = response.header("X-Px", "")).equals("")) {
            return via;
        }

        if (!(via = response.header("Fw-Via", "")).equals("")) {
            return via;
        }
        return via;
    }

    private static String ctype(okhttp3.Response response) {
        MediaType mediaType = response.body().contentType();
        if (mediaType == null) {
            return "";
        }
        return mediaType.type() + "/" + mediaType.subtype();
    }

    private static JSONObject buildJsonResp(byte[] body) throws Exception {
        String str = new String(body, Constants.UTF_8);
        // 允许 空 字符串
        if (StringUtils.isNullOrEmpty(str)) {
            return new JSONObject();
        }
        return new JSONObject(str);
    }

    private static ResponseInfo buildResponseInfo(okhttp3.Response response, String ip, long duration, final long totalSize) {
        int code = response.code();
        Log.d("result","result code = " + code);
        String reqId = response.header("X-Reqid");
        String strEtag = response.header("Etag");
        if(strEtag == null || strEtag.length() == 0){
            strEtag = response.header("ETag");
        }
        reqId = (reqId == null) ? null : reqId.trim().split(",")[0];
        byte[] body = null;
        String error = null;
        try {
            body = response.body().bytes();
        } catch (IOException e) {
            error = e.getMessage();
        }
        JSONObject json = null;
        String strType = ctype(response);
        if (ctype(response).equals(Client.XmlMime) && body != null) {
            try {
                json = buildJsonResp(body);
                if (response.code() != 200) {
                    String err = new String(body, Constants.UTF_8);
                    error = json.optString("error", err);
                }
            } catch (Exception e) {
                if (response.code() < 300) {
                    error = e.getMessage();
                }
            }
        } else {
            error = body == null ? "null body" : new String(body);
        }

        HttpUrl u = response.request().url();
        return ResponseInfo.create(json, code, reqId, strEtag,response.header("X-Log"),
                via(response), u.host(), u.encodedPath(), ip, u.port(), duration,
                getContentLength(response), error, totalSize);
    }

    private static long getContentLength(okhttp3.Response response) {
        try {
            RequestBody body = response.request().body();
            if (body == null) {
                return 0;
            }
            return body.contentLength();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static void onRet(okhttp3.Response response, String ip, long duration,
                              final long totalSize, final CompletionHandler complete) {
        final ResponseInfo info = buildResponseInfo(response, ip, duration, totalSize);

        AsyncRun.runInMain(new Runnable() {
            @Override
            public void run() {
                complete.complete(info, info.response);
            }
        });
    }

    public void asyncSend(final Request.Builder requestBuilder, StringMap headers,
                          final long totalSize, final CompletionHandler complete) {
        if (headers != null) {
            headers.forEach(new StringMap.Consumer() {
                @Override
                public void accept(String key, Object value) {
                    Log.d("HEADERS","key:" + key + " value:" + value.toString());
                    requestBuilder.header(key, value.toString());
                }
            });
        }

//        if (upToken != null) {
//            requestBuilder.header("User-Agent", UserAgent.instance().getUa(upToken.accessKey));
//        } else {
//            requestBuilder.header("User-Agent", UserAgent.instance().getUa("pandora"));
//        }


        final ResponseTag tag = new ResponseTag();
        httpClient.newCall(requestBuilder.tag(tag).build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                int statusCode = NetworkError;
                String msg = e.getMessage();
                if (e instanceof CancellationHandler.CancellationException) {
                    statusCode = ResponseInfo.Cancelled;
                } else if (e instanceof UnknownHostException) {
                    statusCode = ResponseInfo.UnknownHost;
                } else if (msg != null && msg.indexOf("Broken pipe") == 0) {
                    statusCode = ResponseInfo.NetworkConnectionLost;
                } else if (e instanceof SocketTimeoutException) {
                    statusCode = ResponseInfo.TimedOut;
                } else if (e instanceof java.net.ConnectException) {
                    statusCode = ResponseInfo.CannotConnectToHost;
                }

                HttpUrl u = call.request().url();
                ResponseInfo info = ResponseInfo.create(null, statusCode, "", "","", "", u.host(),
                        u.encodedPath(), "", u.port(), tag.duration, -1, e.getMessage(), totalSize);

                complete.complete(info, null);
            }

            @Override
            public void onResponse(Call call, okhttp3.Response response) throws IOException {
                if(response.code() != 200)
                    Log.d("gyb","reponse = " + response.toString());
                ResponseTag tag = (ResponseTag) response.request().tag();
                onRet(response, tag.ip, tag.duration, totalSize, complete);
            }
        });
    }

    public void asyncPost(String url, byte[] body,
                          StringMap headers,
                          final long totalSize, ProgressHandler progressHandler,
                          CompletionHandler completionHandler, UpCancellationSignal c) {
        asyncPost(url, body, 0, body.length, headers, totalSize, progressHandler, completionHandler, c);
    }

    public void asyncPost(String url, byte[] body, int offset, int size,
                          StringMap headers,
                          final long totalSize, ProgressHandler progressHandler,
                          CompletionHandler completionHandler, CancellationHandler c) {
        if (converter != null) {
            url = converter.convert(url);
        }

        RequestBody rbody;
        if (body != null && body.length > 0) {
            MediaType t = MediaType.parse("");
            if (headers != null) {
                Object ct = headers.get(ContentTypeHeader);
                if (ct != null) {
                    t = MediaType.parse(ct.toString());
                }
            }
            rbody = RequestBody.create(t, body, offset, size);
        } else {
            rbody = RequestBody.create(null, new byte[0]);
        }
        if (progressHandler != null || c != null) {
            rbody = new CountingRequestBody(rbody, progressHandler, totalSize, c);
        }

        Request.Builder requestBuilder = new Request.Builder().url(url).post(rbody);
        asyncSend(requestBuilder, headers, totalSize, completionHandler);
    }

    public void asyncPut(String url, byte[] body, int offset, int size,
                          StringMap headers,
                          final long totalSize, ProgressHandler progressHandler,
                          CompletionHandler completionHandler, CancellationHandler c) {
        if (converter != null) {
            url = converter.convert(url);
        }

        RequestBody rbody;
        if (body != null && body.length > 0) {
            MediaType t = MediaType.parse("");
            if (headers != null) {
                Object ct = headers.get(ContentTypeHeader);
                if (ct != null) {
                    t = MediaType.parse(ct.toString());
                }
            }
            rbody = RequestBody.create(t, body, offset, size);
        } else {
            rbody = RequestBody.create(null, new byte[0]);
        }

//        final MultipartBody.Builder mb = new MultipartBody.Builder();
//        mb.addFormDataPart("file", "test", rbody);
//
//        headers.forEach(new StringMap.Consumer() {
//            @Override
//            public void accept(String key, Object value) {
//                mb.addFormDataPart(key, value.toString());
//            }
//        });
//        mb.setType(MediaType.parse("multipart/form-data"));
//        RequestBody mbBody = mb.build();

        if (progressHandler != null || c != null) {
            rbody = new CountingRequestBody(rbody, progressHandler, totalSize, c);
        }

        Request.Builder requestBuilder = new Request.Builder().url(url).put(rbody);
        asyncSend(requestBuilder, headers, totalSize, completionHandler);
    }

    public void asyncMultipartPost(String url,
                                   PostArgs args,
                                   ProgressHandler progressHandler,
                                   CompletionHandler completionHandler,
                                   CancellationHandler c) {
        RequestBody file;
        long totalSize;
        if (args.file != null) {
            file = RequestBody.create(MediaType.parse(args.mimeType), args.file);
            totalSize = args.file.length();
        } else {
            file = RequestBody.create(MediaType.parse(args.mimeType), args.data);
            totalSize = args.data.length;
        }
        asyncMultipartPost(url, args.params, totalSize, progressHandler, args.fileName, file, completionHandler, c);
    }

    private void asyncMultipartPost(String url,
                                    StringMap fields,
                                    final long totalSize,
                                    ProgressHandler progressHandler,
                                    String fileName,
                                    RequestBody file,
                                    CompletionHandler completionHandler,
                                    CancellationHandler cancellationHandler) {
        if (converter != null) {
            url = converter.convert(url);
        }
//        final MultipartBody.Builder mb = new MultipartBody.Builder();
//        mb.addFormDataPart("file", fileName, file);
//
//        fields.forEach(new StringMap.Consumer() {
//            @Override
//            public void accept(String key, Object value) {
//                mb.addFormDataPart(key, value.toString());
//            }
//        });
//        mb.setType(MediaType.parse(""));
//        RequestBody body = RequestBody.create(MediaType.parse(""),file);
        if (progressHandler != null || cancellationHandler != null) {
            file = new CountingRequestBody(file, progressHandler, totalSize, cancellationHandler);
        }
        Request.Builder requestBuilder = new Request.Builder().url(url).post(file);
        asyncSend(requestBuilder, fields, totalSize, completionHandler);
    }

    public void asyncGet(String url, StringMap headers,
                         CompletionHandler completionHandler) {
        Request.Builder requestBuilder = new Request.Builder().get().url(url);
        asyncSend(requestBuilder, headers, 0, completionHandler);
    }

    public ResponseInfo syncGet(String url, StringMap headers) {
        Request.Builder requestBuilder = new Request.Builder().get().url(url);
        return send(requestBuilder, headers);
    }

    public ResponseInfo syncDelete(String url, StringMap headers) {
        Request.Builder requestBuilder = new Request.Builder().delete().url(url);
        return send(requestBuilder, headers);
    }

    private ResponseInfo send(final Request.Builder requestBuilder, StringMap headers) {
        if (headers != null) {
            headers.forEach(new StringMap.Consumer() {
                @Override
                public void accept(String key, Object value) {
                    requestBuilder.header(key, value.toString());
                }
            });
        }

//        requestBuilder.header("User-Agent", UserAgent.instance().getUa(""));
        long start = System.currentTimeMillis();
        okhttp3.Response res = null;
        ResponseTag tag = new ResponseTag();
        Request req = requestBuilder.tag(tag).build();
        try {
            res = httpClient.newCall(req).execute();
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseInfo.create(null, NetworkError, "", "","", "",
                    req.url().host(), req.url().encodedPath(), tag.ip, req.url().port(),
                    tag.duration, -1, e.getMessage(), 0);
        }

        return buildResponseInfo(res, tag.ip, tag.duration, 0);
    }

    public ResponseInfo syncMultipartPut(String url, PostArgs args) {
        RequestBody file;
        long totalSize;
        if (args.file != null) {
            file = RequestBody.create(MediaType.parse(args.mimeType), args.file);
            totalSize = args.file.length();
        } else {
            file = RequestBody.create(MediaType.parse(""), args.data);
            totalSize = args.data.length;
        }
        return syncMultipartPut(url, args.params, totalSize, args.fileName, file);
    }

    private ResponseInfo syncMultipartPut(String url,
                                           StringMap fields,
                                           final long totalSize,
                                           String fileName,
                                           RequestBody file) {
        Request.Builder requestBuilder = new Request.Builder().url(url).put(file);
        return syncSend(requestBuilder, fields,totalSize);
    }

    public ResponseInfo syncMultipartPost(String url, PostArgs args) {
        RequestBody file;
        long totalSize;
        if (args.file != null) {
            file = RequestBody.create(MediaType.parse(args.mimeType), args.file);
            totalSize = args.file.length();
        } else {
            file = RequestBody.create(MediaType.parse(""), args.data);
            totalSize = args.data.length;
        }
        return syncMultipartPost(url, args.params, totalSize, args.fileName, file);
    }

    private ResponseInfo syncMultipartPost(String url,
                                           StringMap fields,
                                           final long totalSize,
                                           String fileName,
                                           RequestBody file) {
//        final MultipartBody.Builder mb = new MultipartBody.Builder();
//        mb.addFormDataPart("file", fileName, file);
//
//        fields.forEach(new StringMap.Consumer() {
//            @Override
//            public void accept(String key, Object value) {
//                mb.addFormDataPart(key, value.toString());
//            }
//        });
//        mb.setType(MediaType.parse("multipart/form-data"));
//        RequestBody body = mb.build();
        Request.Builder requestBuilder = new Request.Builder().url(url).post(file);
        return syncSend(requestBuilder, fields,totalSize);
    }

    public ResponseInfo syncSend(final Request.Builder requestBuilder, StringMap headers,
                                 final long totalSize) {
        if (headers != null) {
            headers.forEach(new StringMap.Consumer() {
                @Override
                public void accept(String key, Object value) {
                    Log.d("Header","key = " + key + " value = " + value.toString());
                    requestBuilder.header(key, value.toString());
                }
            });
        }

//        requestBuilder.header("User-Agent", UserAgent.instance().getUa(upToken.accessKey));
        final ResponseTag tag = new ResponseTag();
        Request req = null;
        try {
            req = requestBuilder.tag(tag).build();
            okhttp3.Response response = httpClient.newCall(req).execute();
            return buildResponseInfo(response, tag.ip, tag.duration, totalSize);
        } catch (Exception e) {
            e.printStackTrace();
            int statusCode = NetworkError;
            String msg = e.getMessage();
            if (e instanceof UnknownHostException) {
                statusCode = ResponseInfo.UnknownHost;
            } else if (msg != null && msg.indexOf("Broken pipe") == 0) {
                statusCode = ResponseInfo.NetworkConnectionLost;
            } else if (e instanceof SocketTimeoutException) {
                statusCode = ResponseInfo.TimedOut;
            } else if (e instanceof java.net.ConnectException) {
                statusCode = ResponseInfo.CannotConnectToHost;
            }

            HttpUrl u = req.url();
            return ResponseInfo.create(null, statusCode, "", "","", "", u.host(), u.encodedPath(), "",
                    u.port(), 0, 0, e.getMessage(), totalSize);
        }
    }

    private static class ResponseTag {
        public String ip = "";
        public long duration = -1;
    }
}
