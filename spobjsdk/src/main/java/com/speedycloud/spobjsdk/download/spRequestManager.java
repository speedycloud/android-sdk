package com.speedycloud.spobjsdk.download;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by gyb on 2018/4/11.
 */

public class spRequestManager {
    private static spRequestManager instance;
    private HashMap<String, ArrayList<spMultiResumeDownTask>> downtaskMap;

    public static spRequestManager getInstance() {
        if (instance == null) {
            instance = new spRequestManager();
        }
        return instance;
    }

    private spRequestManager() {
        downtaskMap = new HashMap<>();
    }

    // 执行文件下载任务
    public void excuteDownTask(spMultiResumeDownTask task) {
        task.startDownload();
        if (!downtaskMap.containsKey(task.getTag())) {
            ArrayList<spMultiResumeDownTask> downTasks = new ArrayList<>();
            downtaskMap.put(task.getTag(), downTasks);
        }
        downtaskMap.get(task.getTag()).add(task);
    }

    // 暂停下载任务
    public void resumeDownTask(spMultiResumeDownTask task) {
        task.resumeDownload();
    }

    // 取消与tag的Activity相关的所有任务
    public void cancelRequest(String tag) {

        if (tag == null || "".equals(tag.trim())) {
            return;
        }

        // 暂停与该activity关联的所有下载任务
        if (downtaskMap.containsKey(tag)) {
            ArrayList<spMultiResumeDownTask> downTasks = downtaskMap.remove(tag);
            for (spMultiResumeDownTask downTask : downTasks) {
                if (!downTask.isDownloading() && downTask.getTag().equals(tag)) {
                    downTask.resumeDownload();
                }
            }
        }
    }

    // 取消进程中的所有下载和访问任务
    public void cancleAll() {

        for (Map.Entry<String, ArrayList<spMultiResumeDownTask>> entry : downtaskMap.entrySet()) {
            ArrayList<spMultiResumeDownTask> downTasks = entry.getValue();
            for (spMultiResumeDownTask downTask : downTasks) {
                if (!downTask.isDownloading()) {
                    downTask.resumeDownload();
                }
            }
        }
    }
}
