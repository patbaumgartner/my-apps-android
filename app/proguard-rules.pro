-keepattributes AnnotationDefault,RuntimeVisibleAnnotations
-keepclassmembers class com.patbaumgartner.android.myapps.** {
    *** Companion;
}
-keepclasseswithmembers class com.patbaumgartner.android.myapps.** {
    kotlinx.serialization.KSerializer serializer(...);
}
