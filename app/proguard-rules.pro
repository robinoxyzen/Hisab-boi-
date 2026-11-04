# Debug builds do not shrink. Keep Firebase models if release minify is enabled later.
-keep class com.hisabboi.model.** { *; }
