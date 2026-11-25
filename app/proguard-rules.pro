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

# Firebase App Check & Google Play Integrity rules
-keep class com.google.firebase.appcheck.** { *; }
-keep class com.google.android.play.core.integrity.** { *; }

# Room Database & Data Models Protection (R8 Full Mode Safe)
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** { *; }
-keep class com.example.data.local.** { *; }
-keepclassmembers class com.example.data.local.** { *; }

# Firebase & Reflection Attributes Protection
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin Coroutines Protection
-keep class kotlinx.coroutines.** { *; }

# Google AdMob (Google Mobile Ads SDK) & R8 Full Mode Protection
-keep class com.google.android.gms.ads.** { *; }
-keep interface com.google.android.gms.ads.** { *; }
-keep public class com.google.android.gms.ads.AdView {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public *;
}
-keepclassmembers class * extends com.google.android.gms.ads.AdListener {
    public *;
}
-keepclassmembers class * extends com.google.android.gms.ads.AdLoadCallback {
    public *;
}
-keepclassmembers class * extends com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback {
    public *;
}
-keepclassmembers class * extends com.google.android.gms.ads.FullScreenContentCallback {
    public *;
}
-keepclassmembers class * implements com.google.android.gms.ads.initialization.OnInitializationCompleteListener {
    public *;
}

# Jetpack Credential Manager & Google Identity Services (R8 Full Mode Safe)
-keep class androidx.credentials.** { *; }
-keep interface androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Firebase Cloud Messaging
-keep class com.google.firebase.messaging.** { *; }
-keep class com.example.receiver.MyFirebaseMessagingService { *; }

# Remote Update Checker Model
-keep class com.example.util.AppUpdateInfo { *; }

