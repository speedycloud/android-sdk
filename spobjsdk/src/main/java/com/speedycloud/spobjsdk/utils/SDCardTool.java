package com.speedycloud.spobjsdk.utils;

/**
 * Created by gyb on 2018/4/11.
 */

public class SDCardTool {
    public static boolean ExistSDCard() {
        if (android.os.Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED)) {
            return true;
        } else
            return false;
    }
}
