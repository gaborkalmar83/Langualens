# LinguaLens — Dutch reading with English underneath

An Android app for learning Dutch by reading real Dutch content with the English
translation shown directly beneath each sentence, plus one-tap saving of words and
sentences into AnkiDroid.

Everything translates **on device** with Google ML Kit's Dutch↔English model
(~30 MB, downloaded once, then fully offline). No API keys, no accounts, no data
leaves the phone.

## What it does

**1. Bilingual reader**
Open any Dutch article by pasting a link, or share a page to LinguaLens from Chrome.
The page renders as normal, but every Dutch sentence gets its English underneath in
blue italics. Toggle between per-sentence and per-paragraph mode, or hide the
English until you tap it — so you read Dutch first and only check when stuck.

**2. Selection menu in every app**
Highlight Dutch text anywhere — Chrome, Discord, WhatsApp, Gmail — and pick
**LinguaLens: vertaal** from the selection popup. You get the translation, a "speak it"
button, and a save button.

**3. Floating bubble**
A draggable bubble that sits over other apps. Tap it and LinguaLens reads the visible
screen text via the accessibility service and shows a scrollable Dutch/English list.
Tap any line to save it. Nothing is read unless you tap the bubble.

**4. Vocabulary and practice**
Saved words and sentences keep their surrounding context and source. There's a
built-in SM-2 spaced repetition review screen, Dutch text-to-speech, and:
- direct push into an AnkiDroid deck through AnkiDroid's ContentProvider API
- tab-separated export you can import into desktop Anki

## Building

CI builds it on every push to `main`: see `.github/workflows/build.yml`.
The APK lands in the run's artifacts and in a GitHub release.

Locally:

```
gradle assembleRelease
```

Requires JDK 17 and the Android SDK with platform 34.

## First run

1. Open LinguaLens → **Download model** (needs internet once).
2. For the bubble: grant *Display over other apps*, then enable
   **LinguaLens screen reader** under Settings → Accessibility.
3. For Anki: install AnkiDroid, then hit **Stuur naar Anki** and accept the
   permission prompt.

## Notes

Android does not let one app modify another app's UI. That's why Chrome pages are
rendered inside LinguaLens's own reader, and why Discord/WhatsApp are handled with the
selection menu and the bubble panel instead of drawing inline.
