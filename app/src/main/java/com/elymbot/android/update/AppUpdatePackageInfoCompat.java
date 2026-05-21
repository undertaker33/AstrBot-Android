package com.elymbot.android.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import java.lang.reflect.InvocationTargetException;

final class AppUpdatePackageInfoCompat {
    private AppUpdatePackageInfoCompat() {
    }

    static PackageInfo packageInfo(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return context.getPackageManager().getPackageInfo(
                        context.getPackageName(),
                        PackageManager.PackageInfoFlags.of(0)
                );
            }
            return legacyPackageInfo(context);
        } catch (PackageManager.NameNotFoundException exception) {
            throw new IllegalStateException("Unable to read app package info.", exception);
        }
    }

    static int versionCode(PackageInfo packageInfo) {
        long longVersionCode = packageInfo.getLongVersionCode();
        return (int) Math.min(longVersionCode, Integer.MAX_VALUE);
    }

    private static PackageInfo legacyPackageInfo(Context context) throws PackageManager.NameNotFoundException {
        try {
            Object result = PackageManager.class
                    .getMethod("getPackageInfo", String.class, int.class)
                    .invoke(context.getPackageManager(), context.getPackageName(), 0);
            return (PackageInfo) result;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof PackageManager.NameNotFoundException) {
                throw (PackageManager.NameNotFoundException) cause;
            }
            throw new IllegalStateException("Unable to read app package info.", cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to read app package info.", exception);
        }
    }
}
