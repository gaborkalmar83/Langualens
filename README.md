# LanguaLens

Read in a language you are learning, with the translation shown directly underneath,
and save anything you highlight into AnkiDroid.

Everything translates **on device** with Google ML Kit. Models are downloaded once
(roughly 30 MB per language) and then work fully offline. No API keys, no accounts,
nothing leaves the phone.

## Languages

**Translation**: any pair among ML Kit's 59 languages. Dutch, English, Hungarian,
German, Spanish, French, Macedonian, Croatian, Polish, Italian and 49 more. Both
directions, any combination: Dutch to Hungarian, Spanish to Hungarian, German to
Macedonian, whatever you set.

Serbian and Bosnian are **not** available for translation, because ML Kit has no
on-device model for them. Croatian is the closest supported neighbour.

**App interface**: English, Dutch, Hungarian, German, Spanish, French, Macedonian
and Serbian (Latin script). Pick one in Settings, or leave it on the system default.
The interface language is independent of what you are reading.

## What it does

**1. Bilingual reader**
Paste a link, or share a page to LanguaLens from Chrome. The page renders normally
but every paragraph gets its translation underneath in blue italics. There is also a
per-sentence mode, and a "hide until tapped" mode so you read the original first and
only check yourself when stuck.

**2. Selection menu in every app**
Highlight text anywhere, in Chrome, Discord, WhatsApp or Gmail, and pick
**LanguaLens** from the selection popup. You get the translation, a speak button and
a save button.

**3. Floating bubble**
A draggable bubble over other apps. Tap it and LanguaLens reads the visible screen
through the accessibility service and lists each line with its translation. Tap a
line to save it. Nothing is read unless you tap the bubble.

**4. Vocabulary and practice**
Saved items keep their surrounding context, their language pair and where they came
from. Built-in SM-2 spaced repetition, text to speech in the source language, plus:

- direct push into an AnkiDroid deck via AnkiDroid's ContentProvider API
- tab separated export for desktop Anki

## Setting it up

### 1. Download a translation model

Open the app, pick your **From** and **To** languages, then tap **Download model**.
Needs internet once. After that translation is offline and instant.

### 2. Enable the floating bubble

This needs **two** separate permissions, and on Android 13 and newer there is a third
step that is easy to miss because the accessibility toggle looks broken without it.

**a. Draw over other apps**

Settings → Apps → LanguaLens → **Display over other apps** → allow.
Or tap **Turn on** next to that line in the app's Read tab.

**b. Allow restricted settings** (Android 13+, sideloaded APKs only)

Android blocks accessibility access for apps installed outside the Play Store. The
toggle in step c will appear greyed out, or will switch itself back off, until you do
this:

1. Settings → Apps → **LanguaLens**
2. Tap the **three dots** in the top right corner
3. Tap **Allow restricted settings**
4. Confirm with your PIN or fingerprint

If you do not see that menu item, open the accessibility screen first, try the
toggle, let it fail, then come back. Some builds only reveal the option after a
blocked attempt.

**c. Turn on the accessibility service**

Settings → **Accessibility** → **Installed apps** (or *Downloaded apps* / *Installed
services*, the name varies by manufacturer) → **LanguaLens screen reader** → toggle on
→ Allow.

Then, back in the app's Read tab, tap **Button on**. The bubble appears. Tap the
bubble over any app to translate what is on screen.

The service reads screen text only in the moment you tap the bubble. It does nothing
in the background.

### 3. Connect AnkiDroid

Install AnkiDroid, then in Settings tap **Send to Anki** and accept the permission
prompt. Set the deck name to whatever you like, and leave the note type as `Basic`
unless you have a custom one. Turn on **Send new items straight to AnkiDroid** if you
want every save pushed immediately.

No AnkiDroid? Use **Export** instead. It produces a tab separated file: in Anki use
File → Import, set the field separator to Tab, and allow HTML in fields.

## Building

CI builds on every push to `main`, see `.github/workflows/build.yml`. The APK lands
in the run's artifacts and in a GitHub release. The workflow needs
`contents: write` permission and the repository setting Actions → General → Workflow
permissions set to read and write.

Locally:

```
gradle assembleRelease
```

Needs JDK 17 and the Android SDK with platform 34.

### Signing

The upload keystore is not in this repository. Without it `assembleRelease` still
works, falling back to the debug signing key, which is fine for personal sideloading.

To build with the real key locally, put `langualens-release.jks` in `app/` and create
a `keystore.properties` at the repository root (both are gitignored):

```
storePassword=...
keyAlias=...
keyPassword=...
```

For CI, add four repository secrets: `KEYSTORE_BASE64` (the `.jks` base64 encoded),
`KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. The workflow skips the signing
step when `KEYSTORE_BASE64` is absent.

## Why it works this way

Android does not let one app modify another app's UI. That is why web pages are
rendered inside LanguaLens's own reader rather than injected into Chrome, and why
Discord and WhatsApp are handled through the selection menu and the bubble panel
instead of drawing translations inline.
