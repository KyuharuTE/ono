package moe.ono.ui.handygridview.utils;

import android.os.Build;

/**
 * Created by Administrator on 2017/11/26.
 */

public class SdkVerUtils {
    public static boolean isAboveVersion(int version) {
        int sdkVersion = Build.VERSION.SDK_INT;
        return sdkVersion >= version;
    }

    public static boolean isAbove19() {
        return isAboveVersion(Build.VERSION_CODES.KITKAT);
    }
}
