/*
 * Copyright 2026 InstaPrime contributors
 * Based on HushGram: https://github.com/SysAdminDoc/HushGram
 */
package app.morphe.patches.instagram.misc.icon

/** Camera outline, lens and viewfinder from the supplied InstaPrime icon, in a 100-unit square. */
internal const val INSTA_PRIME_CAMERA_PATH =
    "M42,21.5H58C73,21.5 78.5,27 78.5,42V58C78.5,73 73,78.5 58,78.5H42C27,78.5 21.5,73 21.5,58V42C21.5,27 27,21.5 42,21.5Z " +
    "M42,26.5H58C69.5,26.5 73.5,30.5 73.5,42V58C73.5,69.5 69.5,73.5 58,73.5H42C30.5,73.5 26.5,69.5 26.5,58V42C26.5,30.5 30.5,26.5 42,26.5Z " +
    "M65,50A15,15 0 1,1 35,50A15,15 0 1,1 65,50Z " +
    "M60,50A10,10 0 1,1 40,50A10,10 0 1,1 60,50Z " +
    "M68.8,34.7A3.5,3.5 0 1,1 61.8,34.7A3.5,3.5 0 1,1 68.8,34.7Z"

internal const val INSTA_PRIME_BADGE_CUTOUT_PATH = "M 63.76,58.02 H 68.24 C 72.44,58.02,73.98,59.56,73.98,63.76 V 68.24 C 73.98,72.44,72.44,73.98,68.24,73.98 H 63.76 C 59.56,73.98,58.02,72.44,58.02,68.24 V 63.76 C 58.02,59.56,59.56,58.02,63.76,58.02 Z M 63.76,59.42 H 68.24 C 71.46,59.42,72.58,60.54,72.58,63.76 V 68.24 C 72.58,71.46,71.46,72.58,68.24,72.58 H 63.76 C 60.54,72.58,59.42,71.46,59.42,68.24 V 63.76 C 59.42,60.54,60.54,59.42,63.76,59.42 Z M 70.2,66 A 4.2,4.2,0,1,1,61.8,66 A 4.2,4.2,0,1,1,70.2,66 Z M 68.8,66 A 2.8,2.8,0,1,1,63.2,66 A 2.8,2.8,0,1,1,68.8,66 Z M 71.264,61.716 A 0.98,0.98,0,1,1,69.304,61.716 A 0.98,0.98,0,1,1,71.264,61.716 Z"

/** A monochrome-friendly adaptive foreground, inset to Android's launcher safe area. */
internal fun instaPrimeForeground(tint: String) = """
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:translateX="18" android:translateY="18" android:scaleX="0.72" android:scaleY="0.72">
        <path android:fillColor="$tint" android:fillType="evenOdd" android:pathData="$INSTA_PRIME_CAMERA_PATH" />
    </group>
</vector>
""".trimStart()

/** White tile with gently rounded corners, matching the settings entry and shortcut icon. */
internal val INSTA_PRIME_LAUNCH_XML = """
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:gravity="center" android:width="64dp" android:height="64dp">
        <shape android:shape="rectangle">
            <corners android:radius="9dp" />
            <solid android:color="#FFFFFFFF" />
        </shape>
    </item>
    <item android:gravity="center" android:width="64dp" android:height="64dp">
        <vector android:width="64dp" android:height="64dp" android:viewportWidth="100" android:viewportHeight="100">
            <path android:fillColor="#FF000000" android:fillType="evenOdd" android:pathData="$INSTA_PRIME_CAMERA_PATH" />
        </vector>
    </item>
</layer-list>
""".trimStart()
