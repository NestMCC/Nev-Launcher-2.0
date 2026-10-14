# NEV Performance Engine — Rancangan Implementasi

> Status: menu Performance + penerapan profil grafis ke `options.txt` sudah ada di launcher (belum diuji di perangkat). Modpack belum ada dan belum dipasang otomatis.

## Tujuan
NEV Performance adalah fitur Nev Launcher + dua profil modpack terpisah untuk Minecraft Java Android:
- Minecraft 1.21.11
- Minecraft 26.2

Prioritas: visual vanilla yang jernih, frame pacing stabil, pengurangan stutter, dan pilihan render distance yang realistis. Tidak menjanjikan FPS atau jarak render tertentu pada semua perangkat.

## Prinsip keamanan dan kompatibilitas
1. Jangan mengunduh atau memasang mod secara otomatis sebelum versi game, mod loader, arsitektur, dependensi, lisensi, dan sumber rilis diverifikasi.
2. Jangan menandai mod sebagai kompatibel hanya karena versi game disebut di halaman proyek; dukungan Android/renderer harus diuji terpisah.
3. Simpan konfigurasi per versi game dan sediakan tombol pulihkan pengaturan.
4. Jangan mengubah atau menghapus dunia pengguna saat menerapkan profil.
5. Jika mod menyebabkan crash atau rendering rusak, matikan kandidat itu dan tampilkan petunjuk dari log.
6. Profil awal memakai shader OFF dan render distance konservatif. Naikkan bertahap setelah pengujian.

## Struktur paket yang direncanakan
- `packs/1.21.11/`: manifest, profil grafis, dan daftar mod terverifikasi khusus 1.21.11.
- `packs/26.2/`: manifest dan profil terpisah khusus 26.2.
- `docs/COMPATIBILITY_MATRIX.md`: status verifikasi setiap kandidat.
- Integrasi Android: menu Performance, profil Balanced/Clear Graphics/Performance, penerapan konfigurasi, pemeriksaan versi, dan pembacaan log crash.

## Profil grafis awal (baseline untuk diuji)
- Shader: OFF
- Graphics: Fancy bila stabil; turunkan ke Fast bila perlu
- Clouds: Fast
- Particles: Decreased
- Entity distance: 75%
- Render distance: 8–12 chunks untuk baseline
- Simulation distance: 5–6 chunks
- FPS limit: uji 45 dan 60; pilih yang paling stabil
- VSync: uji ON dan OFF pada perangkat target

Angka di atas adalah nilai awal pengujian, bukan rekomendasi universal atau hasil benchmark.

## Rencana implementasi
1. Audit arsitektur launcher dan lokasi pengelolaan profil/instalasi mod.
2. Verifikasi versi Minecraft dan kompatibilitas mod untuk setiap paket.
3. Buat manifest paket yang mencantumkan sumber, versi, hash, dependensi, dan lisensi.
4. Implementasikan UI Performance di launcher tanpa mengubah alur peluncuran yang sudah ada.
5. Uji pada Android: startup, dunia baru, hutan, desa, air, efek partikel, perpindahan area, dan sesi panjang.
6. Bandingkan dengan baseline tanpa mod: FPS median/1% low bila tersedia, waktu muat, penggunaan RAM, crash, dan visual.
7. Rilis hanya paket yang lolos tes. Jangan menyatakan APK atau paket siap sebelum build dan pengujian nyata selesai.

## Yang sudah diimplementasikan di launcher
- Menu **Performance** di sidebar; layar mengikuti versi game yang sedang dipilih.
- Tiga profil (Balanced, Clear Graphics, Performance) plus pengaturan kustom: render distance, simulation distance, batas FPS, grafis (Fast/Fancy), awan, partikel, dan jarak entitas.
- Pengaturan disimpan **per versi game** (profil global lama dipakai sebagai nilai awal).
- Penerapan ke `options.txt`: urutan baris, gaya akhir baris, dan tanda kutip nilai dipertahankan; kunci inti (`renderDistance`, `simulationDistance`, `maxFps`) ditambahkan bila belum ada, kunci lain hanya diubah bila sudah ada dan yang dilewati dilaporkan.
- Cadangan otomatis sebelum menulis (5 terbaru disimpan) dan tombol **Pulihkan Cadangan Terakhir**; isi sebelum dipulihkan disimpan sebagai `options.txt.nev-prerestore`. Dunia tidak pernah disentuh.
- Petunjuk apakah versi game punya profil dasar (`packs/1.21.11`, `packs/26.2`).
- Logika murni (`performance/NevOptionsEditor.kt`) punya unit test.

## Batasan
- Belum ada mod yang dipasang otomatis (Sodium, Lithium, ImmediatelyFast, Entity Culling) karena dukungan renderer Android belum terbukti; lihat `COMPATIBILITY_MATRIX.md`.
- Nama kunci `options.txt` bisa berbeda pada versi game baru (mis. 26.x). Kunci yang tidak ditemukan dilewati, bukan ditebak.
- Tidak ada klaim FPS atau jarak render; semua angka adalah titik awal pengujian.
- Pengujian di perangkat Android adalah tahap terpisah dan belum dilakukan.
