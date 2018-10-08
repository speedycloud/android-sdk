package com.speedycloud.spobjsdk.http;

import org.json.JSONObject;

/**
 * Created by gyb on 2018/4/3.
 */

public interface CompletionHandler {
    /**
     * 用户自定义的处理对象必须实现的接口方法
     *
     * @param info     响应的调试信息
     * @param response 响应的数据
     */
    void complete(ResponseInfo info, JSONObject response);
}