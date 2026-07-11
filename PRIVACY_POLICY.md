# Privacy Policy for CyberAsk Scanner

**Last updated:** 10 July 2026  
**Effective date:** 10 July 2026  
**App name:** CyberAsk Scanner  
**Developer / Publisher:** CyberAsk  
**Website:** [https://www.cyberask.co.uk](https://www.cyberask.co.uk)  
**Contact:** [https://www.cyberask.co.uk](https://www.cyberask.co.uk) (use the site contact options for privacy requests)

---

## 1. Introduction

CyberAsk Scanner (“**the App**”, “**we**”, “**us**”, or “**our**”) is a mobile client that lets you connect to a **Tenable Nessus** or **Tenable.io** instance that **you** configure, using API credentials that **you** provide.

This Privacy Policy explains what information the App processes, how it is stored, where it is sent, and your choices. It is intended to meet the expectations of the **Google Play Store** Data safety form and general privacy transparency requirements.

By installing or using the App, you agree to this Privacy Policy. If you do not agree, please uninstall the App and do not use it.

---

## 2. Who this App is for

The App is designed for security professionals and organisations that operate or are authorised to access a Nessus / Tenable environment. You are responsible for ensuring you have lawful authority to use any server, API keys, and scan data you access through the App.

---

## 3. Summary (plain language)

| Topic | What CyberAsk Scanner does |
|--------|----------------------------|
| Account with us | **No.** We do not require a CyberAsk account. |
| Data sent to CyberAsk servers | **None** for app functionality. The App does not upload your credentials, scans, or reports to CyberAsk. |
| Where API traffic goes | Only to the **Nessus / Tenable base URL you enter** in Settings. |
| Credentials | Stored **only on your device**, using Android encrypted storage. |
| Analytics / ads / trackers | **None** built into the App. |
| Crash / usage telemetry | **None** sent to CyberAsk. |
| Children | Not directed at children under 13 (see §12). |

---

## 4. Information the App processes

### 4.1 Information you provide

You may enter and store on the device:

- **Server base URL** (for example, your on-premises Nessus URL or a Tenable cloud endpoint)
- **API Access Key** and **API Secret Key**
- **Scanner ID** and related connection preferences (for example polling interval, export timeout)
- Optional **app lock preference** (require biometric / device credential unlock)

These settings are required for the App to call the Nessus/Tenable API on your behalf.

### 4.2 Information obtained from your Nessus / Tenable server

When you use the App, it may retrieve and display data from **your** configured server, including but not limited to:

- Connection / server status  
- Scan lists, scan details, history, hosts, and vulnerabilities  
- Plugin attributes and remediation-related information  
- Groups, agent groups, agents, and scanners  
- Scan templates and scan creation responses  
- Exported report content (for example PDF, CSV, HTML, or Nessus format) when you request an export  

This content is controlled by **your** Tenable/Nessus deployment and permissions. CyberAsk does not host or intermediate that content.

### 4.3 Information stored as local files

If you generate or download a report, the App may save report files in **app-specific storage** on the device (for example under the App’s private Documents area). You may open or share those files using other apps you choose via the Android share/view system.

### 4.4 Information we do **not** collect

CyberAsk does **not**, through this App:

- Create user accounts or profiles on CyberAsk infrastructure  
- Collect advertising identifiers for marketing  
- Run third-party analytics SDKs (for example Firebase Analytics, Facebook SDK) for tracking  
- Sell personal data  
- Access your contacts, precise location, microphone, camera, or SMS  
- Back up App data to cloud backup by design (`allowBackup` is disabled)

---

## 5. How information is used

On-device and API-related data are used solely to:

1. Connect to the Nessus/Tenable endpoint you configure  
2. Authenticate API requests with the keys you provide  
3. Display scans, agents, groups, plugins, and related security data  
4. Start, stop, pause, resume, create, rename, or delete scans (as permitted by your API role)  
5. Export and store reports locally, and optionally open/share them  
6. Optionally lock the App UI using the device biometric / screen lock APIs  
7. Show a local notification when a report is ready (if notification permission is granted)

We do **not** use App data for advertising, profiling for marketing, or resale.

---

## 6. Where data is stored and who receives it

### 6.1 On your device

| Data | Storage |
|------|---------|
| Base URL, API keys, scanner ID, preferences | **EncryptedSharedPreferences** on the device (AndroidX Security crypto) |
| Downloaded reports | App-specific external/internal files storage |
| Biometric preference | Stored as a local preference; biometric templates remain managed by the **device OS**, not by CyberAsk |

Uninstalling the App removes App-private data (including stored settings and reports held in app-specific storage), subject to normal Android behaviour.

### 6.2 Network transmission

- The App requires the **Internet** permission to communicate with the server URL **you** set.  
- Traffic is intended over **HTTPS**. Cleartext HTTP is disabled by the App’s network security configuration.  
- API keys are sent to **your** server in the standard Nessus/Tenable API key header format required by that product.  
- **CyberAsk does not operate a proxy or backend that receives your Nessus API keys or scan results as part of normal App operation.**

### 6.3 Third parties

| Party | Role |
|-------|------|
| **You / your organisation** | Operator of the Nessus or Tenable environment you connect to |
| **Tenable / your hosting provider** | Processes API requests and scan data under **their** terms and your contract with them |
| **Other apps you choose** | If you open or share a report, the receiving app gets the file content you share |
| **Google Play / Android** | Standard platform services (install, updates, OS security). Not used by us for App analytics in this product |

CyberAsk is not responsible for the privacy practices of Tenable, your self-hosted Nessus instance, your network, or third-party apps you use to open shared files. Review those parties’ policies separately.

### 6.4 Optional website links

The App may link to CyberAsk’s website (for example help or this privacy policy). Visiting the website is separate from in-App API usage and may be subject to the website’s own notices, if any.

---

## 7. Permissions

The App requests the following Android permissions:

| Permission | Purpose |
|------------|---------|
| **Internet** | Connect to the Nessus/Tenable URL you configure |
| **Post notifications** | Notify you when a generated report is ready (Android 13+) |

Permissions are used only for the purposes described above. You can revoke notification permission in system settings; report generation can still work without notifications.

**Note:** Report files are stored in **app-specific** storage and do **not** require broad storage or “all files access” permissions on modern Android versions.

---

## 8. Security measures

We apply reasonable technical measures appropriate to a client-side security tool, including:

- Encrypted on-device storage for API credentials and settings  
- HTTPS-only network configuration (no cleartext by default)  
- Android **FileProvider** for sharing reports (no world-readable public dump by design)  
- Optional biometric / device-credential App lock  
- Release builds may use code shrinking/obfuscation  

No method of electronic storage or transmission is 100% secure. You remain responsible for:

- Protecting your device with a strong screen lock  
- Safeguarding API keys and rotating them if compromised  
- Using trusted networks and properly configured TLS certificates on your Nessus server  
- Ensuring only authorised users can unlock the device or the App  

---

## 9. Data retention

- **On device:** Settings and reports remain until you delete them in the App, clear App storage, or uninstall the App.  
- **On your Nessus/Tenable server:** Retention follows **your** server policies and Tenable product settings; the App does not control server-side retention.  
- **CyberAsk servers:** The App does not retain your Nessus credentials or scan data on CyberAsk infrastructure as part of its core functionality.

---

## 10. Your rights and choices

Depending on your jurisdiction (for example UK GDPR / EU GDPR, or other applicable laws), you may have rights to access, correct, delete, or restrict processing of personal data, and to object or lodge a complaint with a supervisory authority.

**For data held only on your device:** you can delete it by clearing App data or uninstalling the App, and by deleting individual reports in the App where that feature is available.

**For data on your Nessus/Tenable server:** exercise rights through your organisation’s administrator and Tenable / your host, not through CyberAsk’s App backend (there is none for that data).

**Privacy requests to CyberAsk** about this App (for example policy questions): contact us via [https://www.cyberask.co.uk](https://www.cyberask.co.uk).

---

## 11. International data transfers

The App itself does not transfer your Nessus credentials or scan results to CyberAsk. Any cross-border processing occurs only as a result of:

- The location of **your** configured Nessus/Tenable endpoint, and/or  
- Your organisation’s own infrastructure and contracts  

If you point the App at a cloud service (for example Tenable.io), that provider’s transfer and privacy terms apply.

---

## 12. Children’s privacy

The App is **not directed to children under 13** (or the minimum age required in your country). We do not knowingly collect personal information from children. If you believe a child has provided data through the App in a way that concerns you, contact us and we will help with reasonable steps (typically limited to guidance, since we do not host App account data).

---

## 13. Google Play Data safety alignment

For the Play Console **Data safety** form, the following description is consistent with this App’s design:

| Data safety topic | Suggested declaration |
|-------------------|------------------------|
| Data collected by the developer | **No** data collected by CyberAsk from the App for our own servers |
| Data shared with third parties by the developer | **No** developer-mediated sharing; user-configured API and user-initiated share only |
| Data processed ephemerally / on device | Credentials and settings **on device**; API calls go to **user-specified** endpoint |
| Security practices | Data encrypted in transit (HTTPS); credentials encrypted at rest on device |
| Data deletion | Uninstall / clear storage; no CyberAsk cloud account to delete |
| Sensitive permissions | Internet; notifications (optional for report-ready alerts) |

**Important:** You (the publisher) should still complete the Data safety form carefully and keep it updated if the App’s behaviour changes (for example if analytics are added later).

---

## 14. Third-party open-source components

The App is built with standard Android and open-source libraries (for example Jetpack Compose, Retrofit/OkHttp, Moshi, AndroidX Security, Biometric). Those libraries run on your device as part of the App. They are not used by us to phone home analytics.

---

## 15. Changes to this Privacy Policy

We may update this Privacy Policy from time to time. The “Last updated” date at the top will change when we do. Material changes may also be reflected on [https://www.cyberask.co.uk/app-privacy.html](https://www.cyberask.co.uk/app-privacy.html) or in release notes. Continued use of the App after an update constitutes acceptance of the revised policy where permitted by law.

---

## 16. Governing context

This policy describes privacy practices for the **CyberAsk Scanner** Android application published by **CyberAsk**. It does not replace:

- Your organisation’s acceptable use or security policies  
- Tenable’s product terms and privacy notices  
- Applicable law in your jurisdiction  

If there is a conflict between an in-App summary and the published web version of this policy, the version hosted at  
[https://www.cyberask.co.uk/app-privacy.html](https://www.cyberask.co.uk/app-privacy.html)  
should be treated as the version linked for Play Store purposes once published.

---

## 17. Contact

**CyberAsk**  
Website: [https://www.cyberask.co.uk](https://www.cyberask.co.uk)  
Privacy policy URL (Play Store): [https://www.cyberask.co.uk/app-privacy.html](https://www.cyberask.co.uk/app-privacy.html)

For privacy questions about CyberAsk Scanner, contact us through the website.

---

*This document is provided for transparency and Play Store listing support. It is not formal legal advice. Consider having counsel review it before publication if you need jurisdiction-specific compliance (UK GDPR, EU GDPR, CCPA/CPRA, etc.).*
