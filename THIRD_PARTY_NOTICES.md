# Third-party notices

Net Download Manager is a modified version of **AB Download Manager**
(https://github.com/amir1376/ab-download-manager), © its authors, licensed under the
Apache License 2.0 (see `LICENSE`). Net Download Manager is not made, endorsed or supported by
the AB Download Manager project.

## Changes made in this version

- video downloader built on yt-dlp (qualities with sizes, playlists, channels, subtitles)
- torrent and magnet downloads built on aria2
- browser extension: "Download this video" bar, quality menu, playlist buttons, link grabber
- IDM-like look: toolbar, Classic Blue light/dark themes, new name and logo
- extras: antivirus scan, clipboard monitor, yt-dlp auto update, download quota, speed schedule,
  Mark of the Web, archive extraction, floating speed window
- the upstream update check is turned off

The full list of changes is the git history of the `net-download-manager` branch.

## Bundled programs

These programs are shipped unmodified inside the installer, in `app/resources/tools`, and run
as separate processes. Their license texts are in `app/resources/licenses`.

| Program | Version | License | Source code |
|---|---|---|---|
| yt-dlp | 2026.10.21 | The Unlicense (public domain) | https://github.com/yt-dlp/yt-dlp |
| FFmpeg (ffmpeg, ffprobe), build by gyan.dev | 9.0.2 | GNU GPL v3 | https://github.com/FFmpeg/FFmpeg/commit/946fcce07b , build scripts: https://www.gyan.dev/ffmpeg/builds/ |
| aria2 | 1.37.0 | GNU GPL v2 or later (with OpenSSL exception) | https://github.com/aria2/aria2/tree/release-1.37.0 |

## Libraries

Libraries used by AB Download Manager keep their own licenses; the app lists them under
About → Open source libraries.
