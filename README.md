# Muse Trio

Satu aplikasi Android berisi tiga akun Muse. Tiap profil login ke
[muse.ai](https://muse.ai) (versi web) dengan akun Meta yang berbeda,
jadi bisa ganti-ganti akun tanpa logout.

Ringan, tanpa iklan, tanpa internet (selain ke muse.ai sendiri),
tanpa tracking.

## Cara kerja

Muse tidak punya API publik dan aplikasi native-nya tidak bisa
di-embed, jadi aplikasi ini adalah wrapper WebView:

- Layar utama memilih Profil 1, 2, atau 3.
- Tiap profil jalan di proses Android-nya sendiri
  (`:muse_p1`, `:muse_p2`, `:muse_p3`).
- Tiap proses memanggil `WebView.setDataDirectorySuffix()` sebelum
  membuat WebView, supaya tiap profil punya cookie dan storage
  sendiri. Hasilnya: tiga login Meta yang terpisah.

## Cara install dan pakai

1. Download APK dari [releases](https://github.com/duckey11/muse-trio/releases),
   install (sideload, izinkan install dari sumber tidak dikenal
   untuk browser/file manager yang dipakai).
2. Buka Muse Trio, tekan **Profil 1**, login dengan akun Meta
   pertama di halaman muse.ai.
3. Kembali ke pemilih profil (tombol back dua kali), tekan
   **Profil 2**, login dengan akun Meta kedua. Begitu juga
   **Profil 3** untuk akun ketiga.
4. Setelah itu tinggal pilih profil untuk ganti akun. Tidak perlu
   login ulang selama cookie masih berlaku.

Di dalam Muse, tombol back kembali ke halaman sebelumnya. Tekan
back sekali lagi untuk kembali ke pemilih profil. Lampiran file,
mic, dan kamera sudah diizinkan ke WebView.

## Batasan (jujur)

- Ini versi **web** muse.ai, bukan aplikasi Muse asli. Tampilannya
  mengikuti web, dan **tidak ada push notification**.
- Kalau Meta memblokir login di WebView, aplikasi ini tidak akan
  jalan. Itu baru bisa dipastikan setelah dicoba di HP.
- APK ini debug-signed. Update berikutnya harus pakai signature
  yang sama, kalau tidak Android menolak install update.
- MinSdk 28 (Android 9 ke atas), targetSdk 34.

## Build sendiri

Di PC normal:

```sh
./gradlew assembleDebug
```

Tanpa Gradle daemon (mis. sandbox ini):

```sh
./build-manual.sh
```

Butuh JDK 17, Android SDK (platform 34, build-tools 34.0.0),
dan Kotlin compiler 1.9.22.
