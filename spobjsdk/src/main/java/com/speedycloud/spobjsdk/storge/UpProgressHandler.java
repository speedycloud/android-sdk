package com.speedycloud.spobjsdk.storge;

/**
 * Created by gyb on 2018/4/3.
 */

public interface UpProgressHandler {

    /**
     * 用户自定义进度处理类必须实现的方法
     *
     * @param key     上传文件的保存文件名
     * @param percent 上传进度，取值范围[0, 1.0]
     */
    void progress(String key, long totalSize, double percent);
}
