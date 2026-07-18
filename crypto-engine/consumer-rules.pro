# Consumer ProGuard rules para módulos que consuman crypto-engine
-keep class com.zilch.crypto.CryptoEngine { public *; }
-keep class com.zilch.crypto.exception.** { *; }
-keep class com.zilch.crypto.qr.QrDecoder$DecodedQr { *; }
-keep class com.zilch.crypto.contact.Contact { *; }
-keep class com.zilch.crypto.hash.NodeIdentifier { public *; }
