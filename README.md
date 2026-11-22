<p align="center">
  <b>English</b> &nbsp;·&nbsp; <a href="README.ru.md">Русский</a>
</p>

# LightBoard

**Screenshots:** https://drive.google.com/drive/folders/19qA0W0ss2sixZEYYyWG34ktCT-dyHhoT?usp=sharing

A lightweight, private keyboard for Android. Two designs (Material and an iOS-style Cupertino), swipe typing, smart suggestions from dictionaries you build yourself, an encrypted clipboard history and a small, fast, battery-friendly core.

- **No internet permission.** No analytics, no ads, no tracking.
- **Encrypted local data.** Clipboard history and learned words are sealed with a key held in the Android Keystore.
- **Light on resources.** One custom-drawn view instead of a view tree, compact dictionaries, background work only on demand.
- **Open source**, Apache License 2.0.

## Features

**Typing**
- Swipe typing with an optional trail, key popups and hints for digits and symbols; the decoder follows your finger speed and also weighs the word pairs you actually use
- Suggestions, next-word prediction and autocorrection from dictionaries built on your device; autocorrection also steers towards the words you have taught it
- 86 keyboard layouts: 26 with hand-made layouts (Latin, Cyrillic, Greek, Hebrew, Arabic, Persian) and 60 built from the language alphabet (more Latin and Cyrillic languages, Armenian, Georgian, Thai, Hindi, Bengali, Tamil, Telugu, Malayalam, Sinhala, Urdu); multilingual input follows the system language list
- Phone, number, PIN and date pads; a dedicated tablet layout with arrow keys
- Auto-capitalisation, automatic period, space after `.` and `,`, smart dash
- Calculator: type `2+2*3=` and get the result in place
- Cursor control by swiping the space bar, or (off by default, replaces the cursor swipe) language switching by swiping it horizontally or vertically, with an adjustable swipe distance; erase-all gesture, Undo button for deleted or pasted text
- Incognito mode: nothing is learned or stored
- Voice input and an emoji panel with recent emoji

**Design**
- Material and Cupertino designs, light, dark and automatic themes, Material colour schemes
- Optional background blur (Cupertino, Android 12+), key height, rounding, font size, bold font option
- Material panel and key colour sliders
- Bottom bar with globe and microphone, language key, swappable icons
- One-handed mode and split keyboard
- Per-app overrides for the most important options

**Clipboard and shortcuts**
- Clipboard history with a retention period, pinned entries and one-tap clearing
- Strings that look like passwords, one-time codes, tokens or recovery phrases are filtered out and not recorded (a heuristic filter, not a guarantee)
- SMS codes offered in the suggestion bar (only the code is read, nothing is stored)
- Recent screenshot in the suggestion bar for image-capable fields
- Autofill suggestions on supported Android versions

**Backup**
- Export and import of settings, learned words and clipboard history
- Anything personal (learned words, clipboard) is always encrypted with your password: AES-256-GCM, PBKDF2-HMAC-SHA256 with 600,000 iterations
- Password fields are hidden by default and have a show/hide button

**Languages of the interface:** Русский, English, Українська, Deutsch, Español, Português, Français, Italiano, Türkçe, Polski, Bahasa Indonesia, Tiếng Việt.

## Privacy and security

- The app declares no `INTERNET` permission and has `allowBackup` disabled.
- Learned words, word pairs and the clipboard history live in files encrypted with AES-256-GCM; the key never leaves the Android Keystore.
- Nothing is learned and the clipboard history is not recorded in password, e-mail, address and name fields, in fields that forbid personalised learning, or in incognito mode.
- Permissions are requested only when the matching feature is turned on: microphone (voice input), SMS (codes), photos (recent screenshots).
- Voice input uses the speech service of your device. If a network recognizer is selected there, its provider receives the audio under its own policy; LightBoard itself cannot reach the internet.
- Details: [`PRIVACY.txt`](PRIVACY.txt), [`SECURITY.md`](SECURITY.md).

## Dictionaries

Dictionaries are built on your device from frequency lists (one `word count` per line), in *Settings → Dictionaries*:

1. **Download a dictionary** opens a source list in your browser (LightBoard itself has no network access).
2. **Import a dictionary** builds the compact dictionary from the file you saved.

The language is taken from the file name (for example `en_50k.txt`) or detected from the words. Lists with counts are sorted by frequency, files up to 96 MB are accepted. After the import the source file can be deleted, the app keeps its own compact copy; every imported dictionary has a *Delete* button with a confirmation. Source licenses are listed in [`NOTICE`](NOTICE).

## What's new in 1.1.0

- **Dictionaries:** two-step download and import, deleting each dictionary separately, import that respects word counts, a file size limit.
- **Swipe typing:** more accurate (real finger timing, word-pair context, keys of any script), decoding runs off the main thread, a stuck-gesture case and missing right-edge letters with the number row are fixed.
- **Suggestions and autocorrection** use the words and word pairs you have learned.
- **One engine for both designs:** Material and Cupertino behave the same and differ only in look and layout.
- **Backup:** show/hide button on password fields, files are read in the background.
- **Clipboard:** history is not recorded in fields that forbid personalised learning.
- **Language swipe on the space bar:** adjustable activation distance.
- **Android 16:** the app now compiles and targets API 36.

## Build

```
gradle assembleRelease
```

Create `keystore.properties` to sign a release (the file is git-ignored). Without it the build is signed with the debug key and prints a warning; `-Plb.strictSigning=true` turns the warning into an error. Unit tests: `gradle testDebugUnitTest`.

Requirements: Android 6.0 (API 23) or newer. Built with Android Gradle Plugin 8.13, Gradle 8.13 and JDK 17; compiles and targets Android 16 (API 36).

## Support the project

LightBoard is free. If it is useful to you, you can support its development:

| Boosty | USDT (TRC-20) | TON / USDT (TON network) |
|:--:|:--:|:--:|
| [boosty.to/anlocky/donate](https://boosty.to/anlocky/donate) | <img src="docs/qr-usdt-trc20.png" width="150" alt="USDT TRC-20 QR code"> | <img src="docs/qr-ton.png" width="150" alt="TON QR code"> |
| | `TVTsixvbheZMNhD46SJELECFEw1ba5xknh` | `UQBKbiajQEHpBDEnwa9KRLcUs8mcl9fQpMA4xukmCCLqSXgn` |

Send only USDT on the Tron network to the first address and only TON or USDT on the TON network to the second one, otherwise the funds can be lost.

## License and documents

Licensed under the Apache License 2.0.

- [`LICENSE`](LICENSE), [`NOTICE`](NOTICE): license and notices
- [`PRIVACY.txt`](PRIVACY.txt): privacy policy
- [`CONTRIBUTING.md`](CONTRIBUTING.md), [`SECURITY.md`](SECURITY.md): contributing and reporting vulnerabilities

Names and trademarks of other companies belong to their owners. The project is independent and is not affiliated with them.
