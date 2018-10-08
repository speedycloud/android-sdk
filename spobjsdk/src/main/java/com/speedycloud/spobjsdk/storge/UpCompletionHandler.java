package com.speedycloud.spobjsdk.storge;

import com.speedycloud.spobjsdk.http.ResponseInfo;

import org.json.JSONObject;

/**
 * Created by gyb on 2018/4/3.
 */

public interface UpCompletionHandler {

    /**
     * 用户自定义的内容上传完成后处理动作必须实现的方法
     * 建议用户自己处理异常。若未处理，抛出的异常被直接丢弃。
     *
     * @param key      文件上传保存名称
     * @param info     上传完成返回日志信息
     * @param response 上传完成的回复内容
     */
    void complete(String key, ResponseInfo info, JSONObject response);
}
