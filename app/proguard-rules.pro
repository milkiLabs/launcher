# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Needed when minifying the benchmark androidTest APK (error-prone annotations).
-dontwarn javax.lang.model.element.Modifier

# Crash-log friendliness: keep shrinking and optimization, but do not rename
# classes/methods. User-reported stack traces stay readable, so there is no
# dependency on per-release mapping.txt archives.
-dontobfuscate

# Keep file names and line numbers in stack traces for diagnosis.
-keepattributes SourceFile,LineNumberTable
