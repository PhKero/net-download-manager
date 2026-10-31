<p align="center"><img src="desktop/app/icons/icon.png" width="96" alt=""></p>

<h1 align="center">Net Download Manager</h1>

<p align="center">A free, open-source download manager for Windows with an IDM-like feel:<br>
fast multi-part downloads, a "Download this video" button on every site, playlists, torrents.</p>

<p align="center"><a href="#عربي">العربية</a></p>

Net Download Manager is a modified version of [AB Download Manager](https://github.com/amir1376/ab-download-manager)
(Apache-2.0). It is not affiliated with AB Download Manager or with Internet Download Manager (IDM).

## Why use it

- **Free and open source, for good.** No licence key, no serial to be blocked, no trial that runs out.
- **It never nags.** No pop-ups asking you to buy or update. It checks for a new version once a week
  and only shows a small notification (you can turn that off).
- **Big downloads survive a dropped connection.** If the internet goes away or the laptop sleeps,
  the download picks up by itself as soon as the connection is back.
- **Streams in one file.** Video pages and HLS / M3U8 streams are saved as one mp4.

## Features

**Downloads**
- multi-part accelerated downloads with pause / resume, queues, scheduler, shut down when done
- automatic resume when the internet comes back; real file names even when a site hides them in the link
- Grabber: collect all images, videos or documents from a site in 3 steps
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
- category tree, Q / Last Try Date / Description columns, Options in tabs
- English and Arabic (right to left); the Setup asks which one

## Install

Download `NetDownloadManager_*_windows_x64.exe` from [Releases](../../releases) and run it.
After the install, a page shows how to add the browser extension:
- **Firefox:** [addons.mozilla.org](https://addons.mozilla.org/firefox/addon/net-download-manager/), click *Add to Firefox*
- **Edge:** [Edge Add-ons](https://microsoftedge.microsoft.com/addons/detail/lccdhoiokomnkflbefggcbofjimmnlah), click *Get*
- **Chrome / Brave:** `chrome://extensions`, turn on *Developer mode*, *Load unpacked*, pick the `addons\chrome` folder of the app

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

**ليه تستخدمه؟**
- **مجاني ومفتوح المصدر على طول:** مفيش سيريال يتقفل، ولا فترة تجربة بتخلص.
- **مبيزنّش:** مفيش شبابيك بتطلب منك تشتري أو تحدّث. بيدور على تحديث مرة في الأسبوع، وبيقولك في إشعار صغير بس.
- **التحميل الكبير مبيضيعش:** لو النت فصل أو اللابتوب نام، التحميل بيكمل لوحده أول ما النت يرجع.

- تحميل سريع بأجزاء، إيقاف وتكملة، طابور وجدولة، وقفل الجهاز بعد التحميل
- أسماء الملفات بتطلع صح حتى لو الموقع مخبي الاسم جوه اللينك
- جامع الملفات: بيجمعلك الصور أو الفيديوهات أو المستندات من موقع في 3 خطوات
- زرار **"Download this video"** على أي فيديو في أي موقع، وقايمة الجودات بأحجامها
- تحميل البلاي ليستات والقنوات كاملة، والترجمة
- تورنت و Magnet
- فحص الأنتي فيروس، مراقبة الـ Clipboard، فك الضغط، وشباك عايم بالسرعة
- ثيم أزرق فاتح وغامق بيمشي مع الويندوز، والواجهة بالعربي

**التسطيب:** حمّل ملف الـ Setup من صفحة [Releases](../../releases) وشغّله. بعد التسطيب هتظهرلك صفحة فيها خطوات إضافة المتصفح: Firefox و Edge من المتاجر، و Chrome بـ Load unpacked.

</div>
