# Niko Reminders

Reminders in a familiar voice, with a photo, for people who can't easily read a screen: a family member records "take the white pill", and it plays with the photo at the set time. Part of Niko: simple, free, offline Android apps with no ads and no tracking.

## Where the context lives

The private Hub repo at `../Hub` (relative to this repo) is the source of truth. Read the relevant doc before changing behaviour, and don't repeat its reasoning here.

- Reminders spec: `../Hub/docs/02-product/apps/03-reminders/01-spec.md`
- Scope decision: `../Hub/docs/07-decisions/0037-reminders-scope.md`
- Design system (colour, type, layout, components, speech): `../Hub/docs/04-design-system/`
- Words on screen and spoken phrases: `../Hub/docs/03-brand/04-voice-and-tone.md`
- Stack and text-to-speech: `../Hub/docs/06-engineering/01-platform-and-stack.md`
- Alarms, foreground services and audio per Android version: `../Hub/docs/01-research/08-android-platform-facts.md`
- Permissions: `../Hub/docs/06-engineering/04-privacy-and-permissions.md`
- Release, signing, versioning: `../Hub/docs/06-engineering/03-release-and-distribution.md`
- Decisions: `../Hub/docs/07-decisions/`. Anything that constrains future work gets a new ADR there.

If code and the Hub disagree, flag it instead of guessing which is right.

## Stack

- Kotlin, Jetpack Compose, Material 3 as the base layer with Niko's own components on top. No XML views.
- One activity. ViewModel + StateFlow for screen state. DataStore for settings and reminders. No dependency injection framework.
- Dependencies: AndroidX, Kotlin libraries and small GPLv3-compatible open source libraries only, pinned in `gradle/libs.versions.toml`. Never Google Play Services, Firebase, Play libraries, analytics or crash reporting. CI fails on them.
- Shared code (theme, speech, components, PIN pad, settings rows, health checks, setup shell) lives in the public `Commons` repo, included as a git submodule at `commons/` and built as the `:commons` module (ADR 0032). Change it in Commons by pull request, then move the submodule pointer here in its own commit. Keep Reminders-specific logic out of it. Clock and Dialer use the same Commons, so a Commons change must keep both building.
- Recording a voice, adding a photo and spoken time phrases live in Commons (`VoiceRecorder`, `PhotoCropScreen` and `Photos`, `TimePhraser`), shared with Dialer and Clock. Move any other shared part into Commons rather than copying it.

Project skills in `.claude/skills/`: `compose-patterns` and `kotlin-coroutines-flows`.

## Hard rules

- No new permissions without asking. Never the internet permission. The spec lists the permissions Reminders may use; add each only with the feature that needs it.
- A reminder fires on time or the app says plainly why it can't. Never fail silently: exact alarms, battery restrictions and volume are health checks.
- Every control has a spoken label. Touch targets at least 64dp, primary actions such as Done at least 96dp. Text in `sp`, usable at 200% font size. Contrast 7:1 in light and dark.
- Speech gets words, never digits. Each language has a hand-written phrase template.
- No toasts or snackbars for anything important, no icon-only buttons, no swipe-only or long-press-only actions.
- No medical advice. Reminders say what the family recorded; the app never suggests doses or schedules.
- All user-facing text goes in `strings.xml`. Plain everyday words, no exclamation marks, never blame the user.
- Builds must stay reproducible: no timestamps, git hashes or dates in the build, no `+` dependency versions.

## Commands

```sh
./gradlew assembleDebug assembleRelease testDebugUnitTest lint
uvx git-cliff -o CHANGELOG.md   # regenerate the internal changelog
scripts/release.sh 0.1.0        # bump version, changelog, commit and tag
```

Device tests: `../device-tests/ui.py` drives a phone over adb (see `../Clock/HANDOFF.md` and `../device-tests/2026-10-23/results.md` for how the Dialer run went).

## Git and releases

- Conventional commits (`feat:`, `fix:`, `docs:`, `chore:`, `build:`, `ci:`, `test:`, `refactor:`). They generate `CHANGELOG.md`.
- Work on a branch and merge by pull request with rebase. Work is tracked in GitHub Issues.
- No AI attribution in commits, pull requests or changelogs.
- Version lives in `gradle.properties` (`VERSION_NAME`, `VERSION_CODE`). Tags are the bare version name, no `v`. `scripts/release.sh` adds one to the version code, so the first release is versionCode 2.
- User-facing release notes are written by hand in `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`: one point per line starting with `* `, plain words, 500 bytes max.
- The repo is private until the founder decides to open it (ADR 0018). Never change its visibility without that instruction. A release needs the repo public first.
- Releasing: write `changelogs/<next versionCode>.txt`, run `scripts/release.sh <version>` on `main`, then push `main` and the tag. The Release workflow checks the build is reproducible, signs it and publishes the GitHub release with one APK, `niko-reminders.apk` (ADR 0029).
- Signing uses the shared Niko release key (ADR 0034). The founder sets the secrets with `~/.niko/set-release-secrets.sh NikoApps/Reminders`. Never print, copy or commit the key or its password.
- The package ID `io.github.nikoapps.reminders` must never change once released.
- Never commit secrets, signing keys or private Hub content.
