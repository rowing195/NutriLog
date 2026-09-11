package com.watson.nutrilog.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 換桌面圖示：把選到的那個 activity-alias 打開、其餘關掉。
 *
 * Android 不讓 app 在執行時換掉 launcher 圖示（圖示是編譯進 APK 的資源），
 * 所以能換的只有「哪一個 alias 是桌面入口」。細節見 AndroidManifest.xml 的長註解。
 */
object AppIconSwitcher {

    /**
     * **先開新的、再關舊的。** 反過來的話中間會有一瞬間整個 app 沒有任何桌面入口，
     * 有些桌面會趁那一下把圖示從桌面上拿掉，而且不會自己加回來。
     *
     * 用 [PackageManager.DONT_KILL_APP]：不加的話系統會直接把自己殺掉，
     * 使用者按一下設定就閃退出去。
     */
    fun apply(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        setState(pm, context, icon, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        AppIcon.entries.filter { it != icon }.forEach { other ->
            setState(pm, context, other, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        }
    }

    private fun setState(pm: PackageManager, context: Context, icon: AppIcon, state: Int) {
        val component = ComponentName(context.packageName, context.packageName + icon.aliasSuffix)
        // 已經是這個狀態就不要再寫一次：每寫一次桌面都會重整，圖示會閃一下
        if (pm.getComponentEnabledSetting(component) == state) return
        pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
}
