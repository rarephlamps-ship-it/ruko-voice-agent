# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK proguard-android-optimize.txt file.

# Twilio Voice SDK
-keep class com.twilio.** { *; }
-dontwarn com.twilio.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
