# NEV Performance — Matriks Kompatibilitas

Status awal semua kandidat adalah **BELUM DIVERIFIKASI DI ANDROID**. Sebelum dimasukkan ke paket rilis, cek halaman rilis resmi, versi game persis, loader, dependensi, lisensi, dan uji pada renderer Android yang didukung launcher.

| Kandidat | 1.21.11 | 26.2 | Peran | Syarat sebelum dipakai |
|---|---|---|---|---|
| Fabric Loader | Perlu verifikasi | Perlu verifikasi | Mod loader | Rilis yang tepat untuk versi game dan integrasi launcher |
| Sodium | Perlu verifikasi | Perlu verifikasi | Rendering | Versi game persis, renderer Android, dan uji visual/FPS |
| Entity Culling | Perlu verifikasi | Perlu verifikasi | Menghindari render entitas tersembunyi | Versi game, loader, dan uji agar entitas tidak hilang secara salah |
| ImmediatelyFast | Perlu verifikasi | Perlu verifikasi | Optimasi rendering tertentu | Versi game, dependensi, dan renderer Android |
| Lithium | Perlu verifikasi | Perlu verifikasi | Logika/simulasi game | Versi game, loader, dan uji stabilitas dunia |

## Arti status
- **BELUM DIVERIFIKASI DI ANDROID**: jangan masukkan ke paket siap pakai.
- **LOLOS METADATA**: rilis resmi menyebut versi dan loader yang tepat, tetapi belum lolos uji perangkat.
- **LOLOS UJI ANDROID**: lulus startup, gameplay, render, sesi panjang, dan pemeriksaan log di perangkat yang dicatat.
- **DITOLAK**: crash, artefak grafis, konflik, atau lisensi/sumber tidak memenuhi syarat.

## Catatan
Kompatibilitas Minecraft Java desktop tidak otomatis berarti kompatibel dengan Android. Backend grafis seperti GL4ES, ANGLE, atau backend lain dapat mengubah hasil. Catat model perangkat, GPU, versi Android, renderer, loader, dan versi mod pada setiap laporan uji.
