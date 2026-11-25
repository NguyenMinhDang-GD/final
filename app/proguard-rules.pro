-keepattributes *Annotation*,InnerClasses,Signature
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep,includedescriptorclasses class com.fnb.d2c.**$$serializer { *; }
