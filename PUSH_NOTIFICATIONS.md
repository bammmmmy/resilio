# Push notifications (FCM)

This app uses Firebase Cloud Messaging so users get phone notifications for:

- New emergency alerts
- Approved announcements

Weather advisory push is **not enabled yet** (planned for later). Weather still shows in the app dashboards.

Notifications work while the app is closed, as long as the phone has internet (Wi‑Fi or mobile data) and notification permission is allowed.

## One-time setup (required)

Push delivery needs Cloud Functions deployed to your Firebase project (`resilio-ab61f`).

1. Enable the **Blaze** plan for the Firebase project.
2. Install Node.js 20+ and Firebase CLI if needed:
   - https://nodejs.org
   - `npm install -g firebase-tools`
3. From the `resilio` folder:
   ```bash
   firebase login
   cd functions
   npm install
   cd ..
   firebase deploy --only functions
   ```
4. Rebuild and run the Android app. When prompted, allow notifications.
5. Log in once so the device subscribes to topics: `emergency_alerts`, `announcements`.

Deployed functions (alerts/announcements only):

- `notifyOnEmergencyAlert`
- `notifyOnAnnouncementCreated`
- `notifyOnAnnouncementApproved`

## How to verify

1. With the app in the background or force-stopped, create an emergency alert from a BDRRMO/chairman account.
2. Other logged-in devices should show a system notification.
3. Same for an approved announcement.

## Notes

- Android 13+ requires the user to accept **POST_NOTIFICATIONS**.
- Blaze (pay-as-you-go) billing is required for Cloud Functions; light alert/announcement usage often stays near $0.
- Do not put FCM server keys in the Android app; sending is done only by Cloud Functions.
