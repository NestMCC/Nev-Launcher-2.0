# Inventaris Lisensi Pihak Ketiga — NEV Launcher

Status: **audit awal, belum merupakan inventaris lisensi lengkap**.

Dokumen ini mencatat komponen yang terlihat pada konfigurasi build saat pemeriksaan. Nama dan versi berasal dari `gradle/libs.versions.toml` serta konfigurasi modul. Lisensi masing-masing artefak **belum diverifikasi satu per satu dari berkas lisensi resmi atau metadata paket**. Karena itu, jangan menganggap daftar ini sebagai bukti bahwa semua kewajiban distribusi telah dipenuhi.

## Proyek hulu dan atribusi

- **ZalithLauncher2** — https://github.com/ZalithLauncher/ZalithLauncher2  
  Repositori NEV masih memiliki struktur, nama modul, namespace, dan konfigurasi build yang mengacu pada Zalith Launcher. Pertahankan pemberitahuan hak cipta dan lisensi yang melekat pada kode hulu; perubahan branding tidak menghapus hak tersebut.
- **PojavLauncher** — https://github.com/PojavLauncherTeam/PojavLauncher  
  Dicantumkan di README sebagai proyek terkait dalam ekosistem launcher. Hubungan kode dan komponen yang benar-benar digunakan perlu diverifikasi melalui riwayat kode dan dependensi; penyebutan ini bukan bukti bahwa setiap bagian berasal dari PojavLauncher.

## Komponen yang terlihat pada konfigurasi

Daftar berikut adalah titik awal pemeriksaan, bukan pernyataan lisensi:

| Komponen / kelompok | Koordinat atau catatan |
|---|---|
| AndroidX / Jetpack Compose | Banyak artefak `androidx.*`; periksa notice resmi masing-masing artefak |
| Kotlin dan kotlinx.coroutines | `org.jetbrains.kotlin*`, `org.jetbrains.kotlinx:kotlinx-coroutines-android` |
| Google / Dagger Hilt | `com.google.code.gson:gson`, `com.google.dagger:hilt-android`, Android Material |
| Ktor | `io.ktor:ktor-*` |
| OkHttp / Okio | `com.squareup.okhttp3:okhttp`, `com.squareup.okio:okio` |
| LWJGL | Modul lokal di `LWJGL/3.3.3` dan `LWJGL/3.4.1`; termasuk JAR/native binaries yang perlu diinventarisasi |
| SQLCipher | `net.zetetic:sqlcipher-android` |
| Zip4j | `net.lingala.zip4j:zip4j` |
| Apache Commons | `commons-compress`, `commons-codec`, `commons-io` |
| Coil | `io.coil-kt.coil3:coil-*` |
| Material Kolor | `com.materialkolor:*` |
| Lainnya | Gson, MiniDNS, TOML4J, XZ, MMKV, Room, Navigation, Media3, editor, reorderable, dan artefak lain pada version catalog |

Versi dependensi dapat berubah. Untuk rilis tertentu, hasil inventaris harus dibuat dari konfigurasi dan artefak yang benar-benar dipakai oleh varian APK tersebut, termasuk dependensi transitif dan berkas native/JAR/AAR yang disimpan langsung di repositori.

## Tindakan sebelum merilis APK

- [ ] Verifikasi lisensi dan copyright setiap dependensi langsung serta transitif.
- [ ] Periksa `LWJGL/**/libs`, aset runtime, JAR/AAR, pustaka native, font, ikon, gambar, dan aset lain yang dibundel.
- [ ] Pertahankan file lisensi/NOTICE upstream dan tambahkan notice yang diwajibkan setiap komponen.
- [ ] Sediakan *Corresponding Source* sesuai GPL-3.0 untuk distribusi binary yang tercakup GPL, beserta instruksi build yang diperlukan.
- [ ] Pastikan sumber yang diterbitkan cocok dengan commit/varian APK yang didistribusikan.
- [ ] Tinjau identitas aplikasi: konfigurasi saat ini masih menggunakan namespace dan application ID Zalith (`com.movtery.zalithlauncher` ditambah suffix build). Ubah hanya setelah menelusuri dampaknya pada penyimpanan, deep link, autentikasi, dan kompatibilitas; jangan menghapus atribusi upstream.
- [ ] Lakukan build dan pengujian nyata sebelum menandai APK sebagai rilis.

## Prinsip

GPL-3.0 bukan larangan untuk membuat modifikasi atau mendistribusikannya. Namun, distribusi harus memenuhi ketentuan lisensi yang berlaku, mempertahankan pemberitahuan yang diwajibkan, menandai versi modifikasi dengan tepat, dan menyediakan kode sumber yang sesuai bila diwajibkan. Lisensi dependensi dapat berbeda-beda; jangan menyamakan semua komponen secara otomatis dengan GPL-3.0.

Dokumen ini adalah catatan teknis awal, bukan nasihat hukum dan bukan sertifikasi kepatuhan.
