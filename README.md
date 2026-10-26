<p align="center"><img src="desktop/app/icons/icon.png" width="96" alt=""></p>

<h1 align="center">Net Download Manager</h1>

<p align="center">A free, open-source download manager for Windows with an IDM-like feel:<br>
fast multi-part downloads, a "Download this video" button on every site, playlists, torrents.</p>

<p align="center"><a href="#عربي">العربية</a></p>

Net Download Manager is a modified version of [AB Download Manager](https://github.com/amir1376/ab-download-manager)
(Apache-2.0). It is not affiliated with AB Download Manager or with Internet Download Manager (IDM).

## Features

**Downloads**
- multi-part accelerated downloads with pause / resume, queues, scheduler, shut down when done
- speed limit, speed schedule by time of day (optional), download quota (optional)
- torrents and magnet links (aria2)
- categories, proxy, checksum, antivirus scan with Microsoft Defender, "Mark of the Web"
- clipboard monitor, archive extraction after download (7-Zip, optional), floating speed window (optional)

**Video**
- about 1800 sites through [yt-dlp](https://github.com/yt-dlp/yt-dlp): YouTube, Facebook, Instagram, TikTok, X, Vimeo, SoundCloud...
- quality list with sizes (Best, 4K ... 144p, audio only), video and audio merged into mp4
- whole playlists and channels, with a list to untick what you don't want
- subtitles embedded or as `.srt` files
- yt-dlp updates itself once a day

**Browser extension (Firefox, Chrome, Edge, Brave)**
- "Download this video" bar on any video, also inside embedded players
- quality menu with real sizes, read from the app
- Playlist button, "Download this audio" on music sites
- "Download all links" with file type groups, optionally reading linked pages of the same site

**Look**
- toolbar with large colored icons, Classic Blue theme that follows Windows light / dark mode
- Arabic and 25+ other languages

## Install

Download `NetDownloadManager_*_windows_x64.exe` from [Releases](../../releases) and run it.
Then install the browser extension from the same release (`NetDownloadManager-Firefox.zip` /
`NetDownloadManager-Chrome.zip`, load it from `about:debugging` in Firefox or `chrome://extensions`
with Developer mode).

## Build

Needs Windows, JetBrains Runtime 25, Node.js and NSIS. From the project root folder:

```powershell
.\update.ps1 -NoUpdate   # build only
.\update.ps1             # merge the latest AB Download Manager first, then build
```

## Licenses

Apache-2.0, like AB Download Manager. Bundled programs (yt-dlp, FFmpeg, aria2) keep their own
licenses, see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

---

<h2 id="عربي" dir="rtl">عربي</h2>

<div dir="rtl">

**Net Download Manager** برنامج تحميل مجاني ومفتوح المصدر للويندوز، شبه IDM في الإحساس والشكل،
ومبني على [AB Download Manager](https://github.com/amir1376/ab-download-manager).

- تحميل سريع بأجزاء، إيقاف وتكملة، طابور وجدولة، وقفل الجهاز بعد التحميل
- زرار **"Download this video"** على أي فيديو في أي موقع، وقايمة الجودات بأحجامها
- تحميل البلاي ليستات والقنوات كاملة، والترجمة
- تورنت و Magnet
- فحص الأنتي فيروس، مراقبة الـ Clipboard، فك الضغط، وشباك عايم بالسرعة
- ثيم أزرق فاتح وغامق بيمشي مع الويندوز، والواجهة بالعربي

**التسطيب:** حمّل ملف الـ Setup من صفحة [Releases](../../releases) وشغّله، وبعدها سطّب إضافة المتصفح من نفس الصفحة.

</div>
