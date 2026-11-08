# 預設不啟用混淆；若日後開啟，保留 JavascriptInterface 方法名稱。
-keepclassmembers class com.hketa.hybrid.EtaBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
