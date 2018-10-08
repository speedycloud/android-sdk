package com.speedycloud.spobjsdk.listener;

import java.io.File;

/**
 * Created by gyb on 2018/4/11.
 */

public interface spDownLoadStateListener {

    /**
     * 下载进度变化的回调
     *
     * @param process
     */
    void OnDownLoadProcessChange(int process,float percent);

    /**
     * 下载开始的回调
     */
    void OnDownLoadStart(int fileLength);

    /**
     * 暂停下载的回调
     *
     * @param process
     */
    void OnDownLoadResume(int process,float percent);

    /**
     * 下载完成的回调
     */
    void OnDownLoadFinished(File file);

    /**
     * 下载失败的回调
     */
    void OnDownLoadFailed(String error);
}
