# NEV Launcher

<div align="center">

**Launcher Minecraft: Java Edition untuk Android**

Launcher komunitas yang dikembangkan dengan identitas NEV Launcher, dengan fokus pada pengalaman mobile, pengembangan UI, dan peningkatan fitur secara bertahap.

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](./LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![UI: Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Unofficial](https://img.shields.io/badge/Project-Unofficial%20Modification-orange.svg)](#penafian)

</div>

> [!IMPORTANT]
> **NEV Launcher adalah modifikasi tidak resmi.** Proyek ini tidak berafiliasi dengan, tidak didukung oleh, dan tidak disponsori oleh Zalith Launcher, PojavLauncher, Mojang Studios, atau Microsoft. Minecraft adalah produk dan merek dagang pemiliknya masing-masing.

## Daftar isi

- [Tentang proyek](#tentang-proyek)
- [Arah pengembangan](#arah-pengembangan)
- [Unduhan](#unduhan)
- [Pelaporan masalah](#pelaporan-masalah)
- [Lisensi dan kepatuhan](#lisensi-dan-kepatuhan)
- [Inventaris lisensi pihak ketiga](#inventaris-lisensi-pihak-ketiga)
- [Kredit dan proyek hulu](#kredit-dan-proyek-hulu)
- [Penafian](#penafian)

## Tentang proyek

NEV Launcher adalah proyek launcher Android untuk menjalankan Minecraft: Java Edition. Repositori ini digunakan untuk mengembangkan identitas visual NEV Launcher, antarmuka, serta fitur launcher.

| Informasi | Detail |
|---|---|
| Nama proyek | NEV Launcher |
| Versi konfigurasi | 2.7 |
| Platform | Android |
| Teknologi UI | Kotlin dan Jetpack Compose |
| Lisensi proyek | GNU GPL v3.0 — lihat [LICENSE](./LICENSE) |
| Status | Dalam pengembangan |

**Catatan versi:** angka 2.7 merujuk pada versi yang tercantum di konfigurasi proyek. Angka ini bukan jaminan bahwa APK untuk versi tersebut sudah dirilis, berhasil dibangun, atau telah diuji pada semua perangkat.

## Arah pengembangan

Prioritas pengembangan NEV Launcher meliputi:

- Identitas visual dan pengalaman penggunaan yang khas NEV Launcher.
- Antarmuka yang nyaman digunakan pada layar ponsel Android.
- Perbaikan bug dan peningkatan kualitas secara bertahap.
- Pengujian kompatibilitas Minecraft Java Edition, dengan perhatian pada seri 1.21.x.

Fitur yang direncanakan tidak otomatis tersedia pada versi saat ini. Periksa kode, catatan rilis, dan hasil pengujian sebelum mengandalkan suatu fitur.

## Unduhan

Kunjungi halaman [GitHub Releases](https://github.com/NestMCC/NevLauncher/releases).

Jika belum ada rilis APK yang dipublikasikan, berarti belum ada paket rilis yang dapat dipastikan tersedia melalui halaman tersebut. Jangan menganggap build berhasil atau aman diuji sebelum proses build dan pengujiannya benar-benar selesai.

## Pelaporan masalah

Laporkan bug melalui [GitHub Issues](https://github.com/NestMCC/NevLauncher/issues). Jika memungkinkan, sertakan:

- Versi NEV Launcher atau commit yang digunakan.
- Model perangkat dan versi Android.
- Versi Minecraft serta renderer yang dipilih, jika relevan.
- Langkah untuk mereproduksi masalah dan potongan log yang relevan.

**Jangan unggah kata sandi, token akses, kode autentikasi, atau data pribadi** ke issue publik.

## Lisensi dan kepatuhan

Repositori ini menyertakan teks **GNU General Public License v3.0 (GPL-3.0)** di [LICENSE](./LICENSE). Sebelum mendistribusikan, memodifikasi, atau menerbitkan APK, baca lisensi tersebut dan periksa juga lisensi setiap komponen yang digunakan.

Hal penting untuk distribusi versi modifikasi:

1. **Pertahankan pemberitahuan hak cipta dan lisensi** yang berlaku pada kode sumber serta komponen pihak ketiga. Jangan menghapus atribusi pengembang asli.
2. **Tandai perubahan dengan jelas.** Nyatakan bahwa NEV Launcher merupakan versi modifikasi yang berbeda dari proyek hulu, termasuk informasi perubahan dan versi yang relevan.
3. **Sediakan kode sumber yang sesuai.** Jika mendistribusikan APK yang tercakup GPL, ikuti ketentuan GPL-3.0 mengenai penyediaan *Corresponding Source* dan cara distribusinya.
4. **Periksa lisensi dependensi satu per satu.** Tidak semua pustaka yang dipakai sebuah aplikasi harus memiliki lisensi yang sama. Pertahankan pemberitahuan dan penuhi ketentuan masing-masing lisensi, termasuk lisensi permisif maupun copyleft.
5. **Jangan menyatakan kepemilikan atas kode pihak lain.** Perubahan yang dibuat untuk NEV Launcher tidak menghapus hak cipta maupun lisensi yang dimiliki para kontributor sebelumnya.
6. **Nama dan merek tetap terpisah.** Gunakan nama serta identitas NEV Launcher yang berbeda dan jangan membuat pengguna mengira aplikasi ini adalah rilis resmi dari proyek hulu atau Minecraft.

README ini merupakan ringkasan praktis, bukan pengganti teks lisensi. Lihat juga [inventaris lisensi pihak ketiga](./THIRD_PARTY_LICENSES.md), yang saat ini masih berstatus audit awal dan belum memverifikasi lisensi setiap artefak. Jika ada perbedaan, teks lisensi yang berlaku pada komponen terkait dan ketentuan hukum yang berlaku harus diperiksa secara langsung.

### Inventaris lisensi pihak ketiga

Berkas [THIRD_PARTY_LICENSES.md](./THIRD_PARTY_LICENSES.md) mencatat proyek hulu, kelompok dependensi yang terlihat pada konfigurasi build, dan daftar pemeriksaan sebelum rilis. **Dokumen tersebut belum merupakan inventaris lisensi lengkap**: dependensi transitif, aset runtime, serta seluruh JAR/AAR dan pustaka native masih perlu diperiksa untuk varian APK yang benar-benar dirilis.

### Catatan identitas teknis

Pada konfigurasi yang diperiksa, modul Android masih menggunakan namespace dan application ID dasar `com.movtery.zalithlauncher` (nama proyek/modul Gradle sudah `NevLauncher`). Ini merupakan temuan yang perlu ditangani sebagai pekerjaan branding/teknis terpisah. Jangan mengganti application ID secara serampangan karena dapat memengaruhi data aplikasi, autentikasi, tautan, dan kompatibilitas. Apa pun nama aplikasi yang ditampilkan, pertahankan atribusi dan pemberitahuan lisensi proyek hulu.

## Kredit dan proyek hulu

NEV Launcher menggunakan atau mengadaptasi kode dari proyek sumber terbuka. Kredit berikut dicantumkan sebagai titik awal untuk menelusuri proyek hulu; periksa riwayat repositori dan pemberitahuan di masing-masing berkas untuk atribusi yang lebih terperinci.

- [ZalithLauncher2](https://github.com/ZalithLauncher/ZalithLauncher2) — proyek launcher Minecraft Java Edition untuk Android.
- [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) — proyek launcher Minecraft Java Edition untuk Android yang menjadi bagian dari ekosistem teknologi hulu.
- Para kontributor dan pemelihara pustaka pihak ketiga yang tercantum dalam kode, berkas lisensi, dan pemberitahuan masing-masing dependensi.

Daftar ini bukan inventaris lengkap dependensi. Kredit, hak cipta, dan lisensi spesifik harus tetap mengikuti berkas sumber serta komponen yang benar-benar disertakan dalam versi yang didistribusikan.

## Penafian

NEV Launcher adalah proyek komunitas yang tidak resmi. Minecraft, nama, logo, dan aset terkait merupakan milik pemegang hak masing-masing. NEV Launcher tidak menyertakan kepemilikan atas Minecraft dan pengguna tetap bertanggung jawab untuk mematuhi ketentuan layanan serta persyaratan akun yang berlaku.

---

Dikelola di [repositori NEV Launcher 2.0](https://github.com/NestMCC/NevLauncher).
