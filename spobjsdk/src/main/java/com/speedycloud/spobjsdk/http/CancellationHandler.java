package com.speedycloud.spobjsdk.http;

import java.io.IOException;

/**
 * Created by gyb on 2018/4/3.
 */

public interface CancellationHandler {

    /**
     * 定义用户取消数据或文件上传的信号
     *
     * @return 是否已取消
     */
    boolean isCancelled();

    class CancellationException extends IOException {
    }
}
