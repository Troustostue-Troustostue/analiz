# Bütçemiz R8 kuralları
# Uygulama sınıfları küçük; hepsi korunur (yansıma/serileştirme güvenliği), yalnızca kütüphaneler küçültülür.
-keep class com.butcemiz.app.** { *; }
-keepclassmembers class com.butcemiz.app.** { *; }
# Okunabilir hata kayıtları için ad gizleme kapalı
-dontobfuscate
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, SourceFile, LineNumberTable
# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
