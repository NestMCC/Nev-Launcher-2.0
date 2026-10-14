# NEV Performance — Matriks Kompatibilitas

Terakhir diperiksa: 2026-10-15. Metadata versi di bawah mengacu ke halaman rilis resmi Modrinth. **Kompatibilitas desktop tidak membuktikan dukungan Android/renderer**; tidak ada kandidat yang dinyatakan lolos uji perangkat.

## Kandidat untuk Minecraft 1.21.11

| Kandidat | Rilis/metadata | Fungsi | Status Android |
|---|---|---|---|
| Fabric Loader | [Rilis resmi](https://fabricmc.net/use/installer/) — pilih build untuk Minecraft 1.21.11 | Mod loader | Belum diuji di launcher |
| Sodium | [0.8.13 Fabric 1.21.11](https://modrinth.com/mod/sodium/version/Ny3XyYle) | Rendering dan micro-stutter | Belum diuji; jadikan opsional karena backend grafis Android bisa tidak kompatibel |
| Lithium | [0.21.4 Fabric 1.21.11](https://modrinth.com/mod/lithium/version/Ow7wA0kG) | Logika/simulasi | Belum diuji |
| ImmediatelyFast | [Rilis 1.21.11](https://modrinth.com/mod/immediatelyfast/versions) — pilih Fabric yang cocok | Optimasi jalur rendering tertentu | Belum diuji |
| Entity Culling | [Halaman versi](https://modrinth.com/mod/entityculling/versions) mencantumkan 1.21.3–1.21.11 dan Fabric | Menghindari gambar objek tersembunyi | Belum diuji; lisensi perlu diperhatikan saat redistribusi |

## Kandidat untuk Minecraft 26.2

| Kandidat | Rilis/metadata | Fungsi | Status Android |
|---|---|---|---|
| Fabric Loader | [Rilis resmi](https://fabricmc.net/use/installer/) — pilih build untuk Minecraft 26.2 | Mod loader | Belum diuji di launcher |
| Sodium | [0.9.2 Fabric 26.2](https://modrinth.com/mod/sodium/version/mc26.2-0.9.2-fabric) | Rendering dan micro-stutter | Belum diuji; jangan dipasang otomatis sebelum backend Android terverifikasi |
| Lithium | [0.25.2 Fabric 26.2](https://modrinth.com/mod/lithium/version/mc26.2-0.25.2-fabric) | Logika/simulasi | Belum diuji |
| ImmediatelyFast | [1.16.5 Fabric 26.2](https://modrinth.com/mod/immediatelyfast/version/pMWERcSu) | Optimasi jalur rendering tertentu | Belum diuji |
| Entity Culling | [Halaman versi](https://modrinth.com/mod/entityculling/versions) mencantumkan 26.2 dan Fabric | Menghindari gambar objek tersembunyi | Belum diuji; lisensi perlu diperhatikan saat redistribusi |

## Keputusan paket saat ini

- Kedua profil tetap terpisah dan memulai dengan shader OFF.
- Mod belum ditambahkan ke daftar instalasi otomatis karena renderer dan backend OpenGL Android pada launcher belum diuji.
- Jangan menganggap nomor versi mod yang tercantum di sini sebagai bukti bahwa paket aman untuk semua perangkat.
- Setelah uji perangkat, tetapkan versi tepat, dependensi, URL unduhan resmi, hash SHA-512, dan lisensi pada manifest paket. Hindari membundel ulang file yang lisensinya melarang redistribusi.

## Status uji

- [ ] Build launcher dengan menu Performance
- [ ] Uji UI pada Android
- [ ] Uji penerapan options.txt dan pemulihan cadangan
- [ ] Uji tiap mod satu per satu di renderer Android yang dipakai
- [ ] Uji gabungan mod pada dunia baru dan dunia salinan
- [ ] Catat FPS, stutter, waktu pemuatan, RAM, crash, dan artefak visual
