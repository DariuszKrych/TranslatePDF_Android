# Add project specific ProGuard rules here.

# Keep source file + line numbers so Play Console stack traces are useful.
# Upload the generated mapping.txt (app/build/outputs/mapping/release/mapping.txt)
# to Play Console for each release to deobfuscate crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# PdfBox-Android
# Heavy internal reflection for COS object parsing, font loading, CMap lookup.
# Without these keeps, PDDocument.load(...) throws NoClassDefFoundError /
# MissingResourceException in release builds.
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-keep class org.apache.** { *; }
-dontwarn org.apache.**

# Dependencies PdfBox loads by name for PDF encryption / signing code paths.
-keep class org.spongycastle.** { *; }
-dontwarn org.spongycastle.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# javax.xml classes referenced by PdfBox's XMP metadata code.
-keep class javax.xml.** { *; }
-dontwarn javax.xml.**

# Generic signatures + inner classes PdfBox relies on.
-keepattributes Signature,InnerClasses,EnclosingMethod

# ML Kit
# Google ships consumer rules inside each ML Kit AAR, but pin the public
# entry points explicitly as belt-and-suspenders for the translate + text
# recognition paths this app uses.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit** { *; }
-dontwarn com.google.mlkit.**

# Room — entities referenced by name by the generated DAO impl.
-keep class com.dariuszkrych.translatepdf.data.** { *; }
