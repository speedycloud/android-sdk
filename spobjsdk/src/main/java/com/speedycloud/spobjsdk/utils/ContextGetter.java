package com.speedycloud.spobjsdk.utils;

import android.app.Application;
import android.content.Context;

/**
 * Created by gyb on 2018/4/3.
 */

public final class ContextGetter {
    public static Context applicationContext() {
        try {
            Application app = getApplicationUsingReflection();
            return app.getApplicationContext();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Application getApplicationUsingReflection() throws Exception {
        return (Application) Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication").invoke(null, (Object[]) null);
    }
}
