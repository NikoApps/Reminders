# Niko Reminders

Hear a reminder in a familiar voice, with a photo, at the right time. Niko Reminders is for people who can't easily read a screen: people who can't read, older people and people with low vision. Part of [Niko](https://nikoapps.github.io): simple, free Android apps with no ads and no tracking.

**Status:** early development, not released yet. Reminders play, show over the lock screen and keep repeating until Done; device testing comes next.

## What it does

- A family member records the reminder, such as "take the white pill", and adds a photo
- At the set time, the phone plays the recording and shows the photo, with a big Done button
- Medicines first, but it works for anything: prayer, a call, a meal
- A helper sets it up, with settings behind an optional PIN
- No internet permission, no ads, no tracking

## Building

Requires JDK 17 and the Android SDK. Shared code comes from [Niko Commons](https://github.com/NikoApps/Commons) as a git submodule, so clone with `--recursive`, or run `git submodule update --init` in an existing clone.

```sh
./gradlew assembleDebug
```

## Licence

GNU General Public License v3.0. See [LICENSE](LICENSE).
