# Compose and the platform PdfDocument need no extra keep rules; this file
# exists so a release build has somewhere to put project-specific ones.

# PDFBox-Android optionally decodes JPEG2000 through com.gemalto.jp2, which
# is a separate artifact this app does not ship. The reference is dead code
# for our use (encryption only), so tell R8 not to fail on it.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
