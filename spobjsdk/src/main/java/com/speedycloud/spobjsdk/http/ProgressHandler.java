package com.speedycloud.spobjsdk.http;

/**
 * Created by gyb on 2018/4/3.
 */

public interface ProgressHandler {
    /**
     * 用户自定义进度处理对象必须实现的接口方法
     *
     * @param bytesWritten 已经写入字节
     * @param totalSize    总字节数
     */
    void onProgress(long bytesWritten, long totalSize);
}
