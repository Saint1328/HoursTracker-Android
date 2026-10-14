# Hours Tracker — Offline Android app

Personal app for a Samsung Android phone. No Strata repositories or services are required. All timesheets stay on the phone unless explicitly exported. No internet permission is requested by the app.

## Features

- One-tap Clock In and Clock Out, recording the timestamp immediately
- Attempts GPS at clock in/out (requires Android location permissions)
- Select location: Site, Accommodation, RB office, JHB office
- Select function: Audits, Deployments, Processing
- Daily overtime above 8 net hours; 60-minute unpaid break deducted from each completed shift
- Dark/light mode switch, with preference stored on the device
- Weekly/monthly hours and monthly overtime overview
- CSV export through Android file save dialog (transfer file to PC by USB, email etc.)

## Build your APK with a PERSONAL GitHub account (no Android Studio needed)

1. Create/sign in to a **personal** GitHub account at https://github.com/signup. Do not use any Strata organization/repository.
2. Create a **private**, empty repository called `HoursTracker-Android`. Avoid initializing it with a README so the upload is straightforward.
3. On a PC, unzip this project. Upload **the contents of the `HoursTrackerAPK` folder** to the repository, keeping `.github/workflows/build-apk.yml` intact. A Git client is easiest for uploading hidden `.github` folders; alternatively use GitHub's **Add file > Upload files** and ensure the hidden workflow folder is present.
4. Go to the repository's **Actions** tab, select **Build Android APK**, click **Run workflow** (or allow the push build to finish).
5. Open the successful workflow run, then download the **HoursTracker-Android-APK** artifact from the Artifacts section. Extract the downloaded ZIP to obtain `app-debug.apk`.
6. Move `app-debug.apk` to your Samsung phone and open it. Android may request permission to install apps from that file source. Only enable that permission if you trust the APK you built.

APK signing note: this is a debug APK intended for personal testing; Android Gradle Plugin signs it with a debug key. Do not treat it as a production/Play Store release.

## Other build option

Open this project in Android Studio with JDK 17 / Android SDK 35 and use **Build > Build APK(s)**.

## Offline behavior and limitations

- App records timestamps even if GPS is denied or unavailable. GPS may need several seconds for a fix.
- Records are stored in private app preferences and remain on the installed phone. Clearing app data/uninstalling removes records. Export regularly.
- Fixed 60-minute break applies to completed shifts, even short shifts (net hours cannot be negative).
- Overnight shifts are attributed to the clock-in date; no split at midnight.
- The history screen shows recent completed shifts. Editing, deletion, manual entries, and JSON backup are not included yet.
- Exported CSV contains coordinates when available; handle it as private location data.
