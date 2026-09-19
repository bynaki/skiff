-keep class org.bouncycastle.** { *; }
-keep class net.schmizz.** { *; }
-keep class net.i2p.crypto.** { *; }
-keep class uk.uuid.slf4j.android.** { *; }
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn net.schmizz.**
# EdDSAEngine only reaches X509Key for keys that are not EdDSAPublicKey; sshj always passes one.
-dontwarn sun.security.x509.X509Key
