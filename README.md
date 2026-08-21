# LanguaLens

Read in a language you are learning, with the translation shown directly underneath,
and save anything you highlight into AnkiDroid.

Everything translates **on device**. Nothing you read or select is sent to a
translation server. No API keys, no accounts, no tracking.

The Android app lives here; the browser version has its own repository:

| | |
|---|---|
| [`app/`](app) | The Android app. Translates with Google ML Kit. |
| [`langualens-extension`](https://github.com/gaborkalmar83/langualens-extension) | The Chrome extension, in its own repository. Same features, translates with Chrome's built-in translator. |

---

## Where your text goes

Read this first, because the app asks for permissions that sound alarming and you
should be able to check the claims rather than take them on faith.

**Your text is translated locally.** The Android app uses Google ML Kit's on-device
translation; the extension uses Chrome's built-in translator. In both cases the
model file sits on your machine and the translation happens there. The sentences
you read, the words you select and the vocabulary you save are never transmitted to
a translation service.

**Exactly three things touch the network, and nothing else:**

1. **Downloading a language model.** Once per language, from Google's servers
   (Android) or Chrome's model service (extension). What is transferred is the
   model, roughly 30 MB, in one direction. Your text is not part of that request.
   After it lands, translation works with the device in airplane mode.
2. **Opening a link in the reader.** The reader is a browser view, so loading an
   article fetches it from that website exactly as any browser would, and the site
   sees an ordinary visit. This does not apply to text you paste or to text
   captured from your screen — that never leaves the device.
3. **Speaking text aloud.** The speak button hands the text to whichever
   text-to-speech engine is installed on your system. Some engines synthesise
   locally, some do it in the cloud. LanguaLens cannot tell which yours does, so if
   this matters to you, check your TTS engine in your system settings.

**The screen reader reads only when you tap the bubble.** It is not a keylogger and
it does not run in the background. See
[`ScreenReaderService.kt`](app/src/main/java/com/langualens/app/service/ScreenReaderService.kt)
and its call site in
[`BubbleService.kt`](app/src/main/java/com/langualens/app/service/BubbleService.kt):
`readScreen()` is invoked from exactly one place, the bubble's tap handler.

**Your saved words stay put.** They live in a local database (Android) or in the
extension's local storage (Chrome) until you export them or push them to AnkiDroid.
Sending to AnkiDroid is a local app-to-app handoff on your own device.

There are no analytics, no crash reporting, no advertising identifiers and no
network calls other than the three above.

---

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

---

## The Android app

### 1. Bilingual reader

Paste a link, or share a page to LanguaLens from Chrome. The page renders normally
but every paragraph gets its translation underneath in blue italics.

The toolbar across the top has three controls:

- **PARA / SENT** — a translation per paragraph, or one after every sentence.
  Sentence mode only splits blocks that are plain text; anything containing links
  or other markup falls back to paragraph mode, which is what stops it producing
  chopped-up fragments.
- **SHOW / HIDDEN** — hidden mode veils each translation until you tap it, so you
  read the original first and only check yourself when stuck.
- **TRANSLATE** — runs the pass manually. Turn on *Translate automatically when a
  page opens* in Settings to skip it.

**Selecting text** raises three buttons along the bottom — save (★), translate (⇄)
and speak (♪) — and at the same time translates the selection on its own and shows
it as a large popup for three seconds. You do not have to press anything for the
common case. The ⇄ button is still there for selections longer than 400 characters,
which are skipped by the automatic pass because they do not fit in a popup, and for
bringing a popup back after it has faded. Tap the popup to dismiss it early.

### 2. Selection menu in every app

Highlight text anywhere, in Chrome, Discord, WhatsApp or Gmail, and pick
**LanguaLens** from the selection popup. You get the translation, a speak button and
a save button.

### 3. Floating bubble

A draggable bubble over other apps. Tap it and LanguaLens reads the visible screen
through the accessibility service and lists each line with its translation. Tap a
line to save it, long press to hear it.

The panel has three actions:

- **Reader** — sends everything just captured into the bilingual reader, where you
  get sentence mode, hidden mode and the selection tools on the same text.
- **Refresh** — re-reads the screen underneath.
- **Close** — dismisses the panel; the bubble stays.

Nothing is read unless you tap the bubble.

### 4. Vocabulary and practice

Saved items keep their surrounding context, their language pair and where they came
from. Built-in SM-2 spaced repetition, text to speech in the source language, plus:

- direct push into an AnkiDroid deck via AnkiDroid's ContentProvider API
- tab separated export for desktop Anki

---

## Setting up the Android app

### 1. Download a translation model

Open the app, pick your **From** and **To** languages, then tap **Download model**.

*Why:* the translation model is what makes everything else work offline. This is the
one step that needs internet, and it needs it once per language. After it completes
you can put the phone in airplane mode and translation still works.

### 2. Enable the floating bubble

This needs **two** separate permissions, and on Android 13 and newer there is a third
step that is easy to miss because the accessibility toggle looks broken without it.

#### a. Draw over other apps

Settings → Apps → LanguaLens → **Display over other apps** → allow.
Or tap **Turn on** next to that line in the app's Read tab.

*Why:* the bubble is a window drawn on top of whatever app you are using. Android
treats "draw on top of other apps" as a single permission, and there is no narrower
version of it. It lets LanguaLens show the bubble and the translation panel. It does
not grant any ability to read, and it is the same permission a chat head or a screen
recorder's controls use.

#### b. Allow restricted settings (Android 13+, sideloaded APKs only)

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

*Why:* this is a safety catch Google added because malware distributed as a sideloaded
APK used to abuse accessibility. It is not specific to LanguaLens; every sideloaded
app that uses an accessibility service hits it. You are confirming with your own PIN
that you meant to install this. If you would rather not grant it, skip steps b and c
entirely — the reader and the selection menu work without any of it, you just lose
the bubble.

#### c. Turn on the accessibility service

Settings → **Accessibility** → **Installed apps** (or *Downloaded apps* / *Installed
services*, the name varies by manufacturer) → **LanguaLens screen reader** → toggle on
→ Allow.

Then, back in the app's Read tab, tap **Button on**. The bubble appears. Tap the
bubble over any app to translate what is on screen.

*Why:* reading the text of another app's screen is only possible through an
accessibility service — Android offers no other route, and this is the same mechanism
a screen reader for blind users relies on. Android's confirmation dialog lists the
full set of things the API *can* do, which is why it reads so severely; it is
describing the API, not what this app does with it. What LanguaLens does with it is
one call to `readScreen()`, made only from the bubble's tap handler. It does not run
between taps, it does not watch what you type, and there is nowhere for the text to
go because translation happens on the device.

If any of that is more than you want to grant, the bubble is the only feature you
give up.

### 3. Connect AnkiDroid

Install AnkiDroid, then in Settings tap **Send to Anki** and accept the permission
prompt. Set the deck name to whatever you like, and leave the note type as `Basic`
unless you have a custom one. Turn on **Send new items straight to AnkiDroid** if you
want every save pushed immediately.

*Why:* the permission is AnkiDroid's own — `com.ichi2.anki.permission.READ_WRITE_DATABASE`
— and it lets LanguaLens write cards into your local Anki collection. It is a
handoff between two apps on your phone, with no server in between.

No AnkiDroid? Use **Export** instead. It produces a tab separated file: in Anki use
File → Import, set the field separator to Tab, and allow HTML in fields.

---

## The Chrome extension

The browser version lives in its own repository: **[langualens-extension](https://github.com/gaborkalmar83/langualens-extension)**.

Same idea in Chrome — translations underneath each paragraph, the selection bar, the
same automatic popup, and the direction flip on pages already written in your target
language. It translates with Chrome's built-in on-device translator rather than
ML Kit, and needs Chrome 138 or newer.

Installation, usage and the privacy notes are in that repository's README.

---

## Building the Android app

CI builds on every push to `main`, see
[`.github/workflows/build.yml`](.github/workflows/build.yml). The APK lands in the
run's artifacts and in a GitHub release. The workflow needs `contents: write`
permission and the repository setting Actions → General → Workflow permissions set to
read and write.

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

---

## Why it works this way

Android does not let one app modify another app's UI. That is why web pages are
rendered inside LanguaLens's own reader rather than injected into Chrome, and why
Discord and WhatsApp are handled through the selection menu and the bubble panel
instead of drawing translations inline.

The Chrome extension does not have that restriction, which is why it can annotate
pages directly — and why, on desktop, the reader and the "screen" are the same thing.
