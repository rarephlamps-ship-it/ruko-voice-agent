# Twilio Voice SDK — keep all public SDK classes and native methods
-keep class com.twilio.voice.** { *; }
-dontwarn com.twilio.voice.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Keep app classes used for reflection or data binding
-keep class com.ruko.voiceagent.** { *; }

