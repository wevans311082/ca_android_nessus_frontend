# ca_android_nessus_frontend

Android (Jetpack Compose) frontend for Tenable Nessus with:

- User-configurable Nessus URL + API keys
- Scan enumeration and scan result views
- Plugin detail and remediation views
- Group and agent management actions
- Scan settings update and start/stop/delete controls

## Build

```bash
./gradlew assembleDebug
```

For a signed release build (Play Store):

```bash
./gradlew bundleRelease
```

## Test

```bash
./gradlew test
```

## Play Store / Production Notes
- Targets API 35 (required)
- Uses EncryptedSharedPreferences for credentials
- Enforces HTTPS via network security config
- Release builds are minified + resource shrunk (Moshi rules included)
- Privacy policy: https://www.cyberask.co.uk/app-privacy.html

When uploading:
1. Use Android App Bundle (.aab)
2. Complete Data safety section (credentials stored locally only; no data sent to developer)
3. Provide the privacy policy URL above
4. Test the release build thoroughly (especially scan export and agent management)
