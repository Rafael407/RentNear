# RentNear

RentNear is an Android app for renting things from people nearby. An owner lists an item; a renter finds it on a map, negotiates the price in chat, books it, pays from a demo wallet, collects it with a QR handover and condition photos, returns it the same way, and both sides review each other. Damage claims, late fees, debts, a trust score and a points leaderboard keep it honest.

Everything runs on free plans with **no billing card**, and all money is a **demo wallet** (currency ৳). A separate JavaFX desktop client, `RentNearAdmin`, is used by moderators.

> **Status:** version 0.9. The code was written without being compiled or run on a device, and the Firestore rules were written without the Firebase emulator. Expect a few build errors on the first Gradle sync and possibly a rules error on first publish. Paste the error text and it can be fixed.

---

## Contents
1. [Features](#1-features)
2. [Tech stack](#2-tech-stack)
3. [Setup](#3-setup)
4. [Trying it with two accounts](#4-trying-it-with-two-accounts)
5. [How the app works](#5-how-the-app-works)
6. [Project structure](#6-project-structure)
7. [Data model (Firestore)](#7-data-model-firestore)
8. [Realtime Database (presence)](#8-realtime-database-presence)
9. [Android components](#9-android-components)
10. [Security model and honest limits](#10-security-model-and-honest-limits)
11. [Design decisions](#11-design-decisions)
12. [Troubleshooting](#12-troubleshooting)
13. [Admin client](#13-admin-client)
14. [Not included](#14-not-included)

---

## 1. Features

| Area | What it does |
|---|---|
| **Account** | Email/password with verification, or Google sign-in. Banned accounts are signed out. |
| **Home** | Map and Browse tabs over one nearby search (5, 10 or 20 km). Listings appear as green pins; tap one for a preview card. Category filter, sort by distance, price or rating, 20 listings at a time. The last search is cached in Room for instant and offline display. |
| **Listings** | Title, description, category, price per hour or day, replacement value, up to 4 photos, pickup address (search or current location), audience (**Everyone** or **Friends only**). |
| **Item detail** | Photo strip, price, rating, owner, pickup map with walking route and time, buttons for Message, Book, Open in Maps, Share and Reviews. |
| **Chat** | One conversation per item and renter: text, photos and price offers (accept, counter, decline). Block and report. Unread badge. Header shows the other person's **Online / Last seen**. |
| **Bookings** | Request (or book at an agreed price), owner accepts, renter pays from the wallet. The amount is held on the booking until the return is confirmed. |
| **Handover and return** | Owner takes condition photos and shows a QR code; renter scans it and takes photos. The same on return. Late fees after a 15-minute grace period. |
| **Wallet** | Demo balance (৳2000 on sign-up, top-up buttons), history, **CSV export** through the share sheet. |
| **Reviews and points** | Both sides review after completion. Trust score, points, tiers (Bronze, Silver, Gold, Platinum) and a leaderboard. |
| **Claims and debts** | Damage claim within 24 hours of return; accepted claims become a debt with a 10-day deadline, freeze, and ban if unpaid. |
| **Friends** | Add by email or **pick from contacts**; friends see each other's Friends-only listings. |
| **Trusted badge** | Needed to rent items worth more than ৳15,000; requested in Profile, granted by an admin. |
| **Reminders** | Local notifications before a rental is due and before a debt deadline; a foreground **countdown notification** while a rental is in use. |
| **System awareness** | Banner and toast when airplane mode, low battery or battery saver changes. |
| **Settings** | Search radius, reminders on or off, clear saved listings. |

---

## 2. Tech stack

| Layer | Choice |
|---|---|
| Language | Java 17, XML layouts, Material 3, ViewBinding |
| Architecture | MVVM: ViewModel + LiveData + Repository |
| Navigation | Navigation component; one `MainActivity` with fragments, plus three auth activities |
| Backend | Firebase **Spark** plan: Authentication, Cloud Firestore, Realtime Database. No Cloud Functions, no FCM |
| Pictures | Cloudinary free plan, unsigned upload; only URLs are stored in Firestore |
| External API | Geoapify free plan (address search, reverse geocoding, walking routes) via Retrofit + Gson |
| Maps | MapLibre with OpenFreeMap tiles (no key) |
| Geo queries | GeoFire geohash ranges |
| Device | CameraX, ML Kit barcode scanning, ZXing (QR generation), WorkManager, notifications, Contacts |
| Local storage | Room (cached search), SharedPreferences (settings) |

---

## 3. Setup

### 3.1 Android Studio
Open this folder in Android Studio (Koala or newer). Set **Gradle JDK to 17–21** (Settings > Build > Gradle). A newer JDK such as 25 will fail with Gradle 8.7. Let it sync.

### 3.2 Firebase
1. Create a project on the **Spark** plan at <https://console.firebase.google.com>.
2. Add an Android app with package name `com.rentnear.app`.
3. Add your debug SHA-1 (needed for Google sign-in): run `./gradlew signingReport` and paste the `debug` SHA-1 in Project settings.
4. **Authentication > Sign-in method:** enable **Email/Password** and **Google**.
5. **Firestore Database:** create a database, open the **Rules** tab, paste `firestore.rules`, publish.
6. **Realtime Database:** Build > Realtime Database > Create database, open **Rules**, paste `database.rules.json`, publish. (Needed only for the Online / Last seen feature.)
7. Download `google-services.json` (after steps 4 and 6, so it contains the Google client and database URL) and put it in `app/`. It is git-ignored.

### 3.3 Third-party keys (free, no card)
1. **Geoapify:** create a project at <https://myprojects.geoapify.com> and copy the API key.
2. **Cloudinary:** create an account, note the *cloud name*, then Settings > Upload > Upload presets > add a preset with **Signing mode: Unsigned**.
3. Copy the three lines of `local.properties.example` into `local.properties` and fill them in:
   ```
   GEOAPIFY_KEY=...
   CLOUDINARY_CLOUD=...
   CLOUDINARY_PRESET=...
   ```
   Rebuild after editing.

Without the Geoapify key the map and listings still work, but address search and walking routes are off. Without the Cloudinary values, publishing a listing or sending a photo fails with a clear message.

### 3.4 Run
Use a device or emulator with Google Play services. The camera features need a real camera or an emulator webcam. Allow notifications (Android 13+) when asked.

### 3.5 Firestore indexes
Every query uses a single field or equality filters, so no composite index is needed. If Logcat ever prints an index link, open it and press **Create** (free).

---

## 4. Trying it with two accounts

Use two phones or two emulators. **A** = owner, **B** = renter.

1. B registers and verifies the email. A lists an item.
2. B opens the item, messages A, sends an offer; A accepts it.
3. B taps **Book at agreed price**; A accepts the request; B pays (**Profile > Wallet** has demo top-ups).
4. A takes before-photos and shows the handover QR. B scans it, takes photos and confirms. The rental is now **in use**. B can tap **Show countdown in notification**.
5. A shows the return QR. B scans it, takes photos and taps **Return item**. A takes after-photos and confirms; the payment moves to A's wallet.
6. Both leave reviews. Check **Profile > Leaderboard**.
7. Claims: after a return, A taps **Report damage** within 24 hours. B accepts, then pays under **Profile > Debts**.
8. Friends: **Profile > Friends**, add by email or from contacts; the other account accepts.
9. Admin: follow `RentNearAdmin/README.md`.

`DEMO.md` maps each part of a course demonstration to a screen and a talking point.

---

## 5. How the app works

### 5.1 Booking life cycle

```
requested ──accept──► accepted ──pay──► paid ──handover (QR + photos)──► handed_over
    │                    │                                                   │
 decline/cancel       cancel                                    past end time ▼
                                                                         overdue
                                                                             │
handed_over / overdue ──return (QR + photos, late fee)──► returned ──owner confirms──► completed
                                                             │
                                          24 h with no action: renter closes it ──► completed
```

* **Payment:** one batch makes the booking `paid`, lowers the wallet by exactly the total, stores the amount as `escrow` on the booking and adds a history line.
* **Handover and return:** secret codes live in `bookings/{id}/secret/codes`, readable only by the owner. The rules compare the scanned code to it, so a renter cannot fake a scan.
* **Late fee:** half the rate per started unit after a 15-minute grace period, capped at the booking total, taken from the renter's wallet at return.
* **Payout:** on confirmation the escrow plus any late fee goes to the owner's wallet.
* **Overlap check:** accepting is refused if it overlaps another accepted booking for the same item (checked in the app).

### 5.2 Reviews, score and points
* One review per side per booking (`bookingId_reviewerUid`), 1 to 5 stars, optional comment.
* The reviewed person's trust score changes by **stars − 3**; they get **2 × stars** points; the reviewer gets **5** points.
* Tiers: Bronze, Silver (150 points), Gold (500), Platinum (1000).
* A renter's review also updates the item's rating average and count.

### 5.3 Claims, debts, freeze and ban
* The owner can claim up to the item's replacement value within 24 hours of the return, with a reason. One claim per booking; photos are the evidence.
* The renter can accept, dispute (an admin decides) or offer a smaller amount.
* Accepting creates a **debt** due in **10 days**: the renter is **frozen** (cannot list, request, accept or pay, but can still return items), loses **20** trust score and gets the past-damage flag.
* Repaying takes the amount from the wallet, unfreezes when it was the last open debt and gives **10** score back. The owner then taps **Collect**.
* Reminders at 7, 3 and 1 days left. After the deadline the account is **banned**: the app signs the debtor out the next time it opens, and the owner can tap **Ban account**.

### 5.4 Search
A circle around the user is covered by a few geohash ranges. Each range is one Firestore query on the indexed `geohash` field, results are filtered by exact distance, and Friends-only items are filtered by the viewer's friend list. Reads are one-shot to protect the free quota.

### 5.5 Pictures
Photos are scaled to 1280 px JPEG and uploaded to Cloudinary with the unsigned preset. The returned `secure_url` is saved in Firestore.

---

## 6. Project structure

```
RentNear/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/rentnear/app/
│   │   ├── MainActivity.java        host of the fragments, system banner, presence, overdue ban
│   │   ├── RentNearApp.java         app start: Prefs, notification channels
│   │   ├── data/                    Retrofit (Geoapify), Cloudinary uploader, Room cache
│   │   ├── model/                   Item, Booking, Chat, ChatMessage, Review, Claim, Debt, ...
│   │   ├── repository/              Firestore and Realtime Database access (one class per area)
│   │   ├── viewmodel/               ViewModels exposing LiveData to the screens
│   │   ├── ui/                      auth, home, item, chat, booking, wallet, claim, friends,
│   │   │                            profile, settings, common
│   │   ├── receivers/               SystemEventReceiver, BootReceiver
│   │   ├── services/                RentalTimerService (foreground countdown)
│   │   ├── workers/                 booking and debt reminder workers, resync after reboot
│   │   └── util/                    Format, Prefs, QrCode, Tiers, ContactsPicker, CsvExport, ...
│   └── res/                         layouts, navigation graph, strings, menus, file_paths.xml
├── firestore.rules                  Firestore security rules (about 700 lines)
├── database.rules.json              Realtime Database rules
├── local.properties.example         template for the API keys
├── DEMO.md                          demonstration script
└── README.md
```

Data flows one way: **Fragment → ViewModel → Repository → Firebase**, and results come back as **LiveData** observed by the fragment.

---

## 7. Data model (Firestore)

| Path | Contents |
|---|---|
| `users/{uid}` | name, email, photoUrl, trustScore, points, trusted, frozen, banned, pastDamage, warning |
| `items/{id}` | owner, title, description, category, price, unit, declaredValue, photos, address, lat, lng, geohash, audience, active, ratingAvg, ratingCount |
| `bookings/{id}` | item, renter, owner, start, end, rate, unit, total, status, escrow, photos (before/after, both sides), late fee, timestamps |
| `bookings/{id}/secret/codes` | handover and return QR secrets (owner read only) |
| `chats/{itemId_renterId}` | participants, item summary, last message, unread counters, accepted price |
| `chats/{id}/messages/{msg}` | text, image, or offer (amount, unit, state) |
| `wallets/{uid}` | balance; `wallets/{uid}/tx/{id}` history lines |
| `reviews/{bookingId_reviewerUid}` | rating, comment, reviewer, reviewee |
| `claims/{id}`, `debts/{id}` | damage claims and debts with deadlines and states |
| `friendRequests/{from_to}` | from, to, names, status |
| `friends/{uid}/list/{friendUid}` | name, since |
| `trustRequests/{uid}`, `reports/{id}` | moderation queues |
| `admins/{uid}` | accounts allowed to use the admin client (written by hand in the console) |

---

## 8. Realtime Database (presence)

Realtime Database is used for **presence** because its `onDisconnect()` hook marks a user offline even when the app is killed.

```
presence/{uid} = { online: true|false, lastSeen: <server timestamp> }
```

* `PresenceRepository.goOnline()` runs in `MainActivity.onStart`, listens to `.info/connected`, sets the `onDisconnect` write and marks the user online. `goOffline()` runs in `onStop`.
* `ChatFragment` calls `watch(otherUid)` and shows **Online** or **Last seen 5 minutes ago** under the chat title.
* Rules (`database.rules.json`): any signed-in user can read; only the owner can write their own node.
* Everything is wrapped in try/catch. If the database was not created, the rest of the app works and the header simply shows nothing.

---

## 9. Android components

| Component | Where |
|---|---|
| Activities | `SplashActivity`, `LoginActivity`, `RegisterActivity`, `MainActivity` |
| Explicit intents | Login → `MainActivity`; notification tap → booking (`bookingId`, `openDebts` extras) |
| Implicit intents | Open pickup in Maps (`geo:`), share a listing (`ACTION_SEND`), share the wallet CSV |
| Broadcast receivers | `SystemEventReceiver` (dynamic: airplane mode, battery low/okay, battery saver); `BootReceiver` (manifest: re-schedules reminders after boot or app update) |
| Service | `RentalTimerService`, a foreground service with a live countdown and a Stop action |
| Content providers | Contacts (`ContactsContract`, read with a runtime permission) and `FileProvider` for the CSV export |
| Background work | WorkManager: booking reminders, debt reminders, resync after reboot |
| Permissions (runtime) | Camera, notifications, contacts |
| Lifecycle | State is held in ViewModels, so screens survive rotation |

---

## 10. Security model and honest limits

There are no Cloud Functions (they need the paid Blaze plan) and no push notifications. So the **app writes** wallet balances, trust scores, points and debt records, and the **Firestore rules check each write**: allowed state transitions, exact amounts (payments, late fees, payouts, score and point changes), who may write what, one review per booking, and the debt deadline. Each cross-document change must come with a valid new document (for example a score change only together with a new review).

Limits you should know about:
* **Demo wallet:** top-ups are capped per write (৳5000) but not in total. It is demo money, so this is by design.
* **Friends-only listings** are hidden in the app, but the rules let any signed-in user read listings, because a rule cannot be proven for a range query. Someone reading Firestore directly could see them, including the pickup address.
* **Scores and points** change only with a valid review or debt and exact amounts, but the writer is still the client.
* **Bans and freezes** take effect when the app next reads the profile. A debtor who never opens the app is banned when the owner taps Ban or an admin runs the sweep.
* **The booking overlap check** runs in the app, so two requests for the same dates can both exist until one is accepted.
* **Reminders** use WorkManager, so timing is approximate.
* **Presence** can be written by its owner only, but is readable by any signed-in user.
* Production would move wallet, score, debt and ban logic into Cloud Functions.

---

## 11. Design decisions

* Debt deadline **10 days**, reminders at 7, 3 and 1 days. Score penalty 20, 10 back on repayment.
* Late fee: **half the rate** per started unit after **15 minutes** of grace, capped at the total.
* Any part of a unit counts as a full unit; a booking is 30 days at most.
* Identity verification is replaced by an admin-granted **Trusted** badge (needed above ৳15,000).
* Currency symbol ৳ (`Format.CURRENCY`).
* Live Firestore listeners run only on the open chat or booking, and while the app is in the foreground, to protect the free quota.

---

## 12. Troubleshooting

| Problem | Cause and fix |
|---|---|
| Gradle sync fails with a Java or JVM message | Set Gradle JDK to 17–21. |
| `Found item String/xyz more than one time` | A string name is defined twice in `strings.xml`; rename or delete one. |
| `PERMISSION_DENIED` after changing the rules | Make sure you **published** the latest `firestore.rules`. If one action still fails, open the Rules playground, simulate the write and send the failing line. |
| Friend requests fail | Fixed in the current rules: a read of a request that does not exist yet must be allowed. Republish `firestore.rules`. |
| No Online / Last seen in chat | Create the Realtime Database, publish `database.rules.json`, and re-download `google-services.json`. |
| No pins on the map | Check that Browse shows items, that they are inside your radius (Settings), and Logcat for a Firestore error or index link. |
| Address search does nothing | `GEOAPIFY_KEY` is empty in `local.properties`; rebuild after editing. |
| Photo upload fails | Check `CLOUDINARY_CLOUD` and that the preset is **Unsigned**. |
| No notifications | Allow notifications (Android 13+) and disable battery restrictions for the app. |
| Contacts picker is empty | No contact has an email address, or the contacts permission was denied. |

---

## 13. Admin client

`RentNearAdmin/` is a separate JavaFX 21 desktop application (Maven, Firebase Admin SDK). It signs in with an email and password that has an `admins/{uid}` document and offers: dashboard, users (warn, freeze, ban), reports, trusted requests, claims (uphold or reject disputes), debts (including an overdue ban sweep) and listings (remove). Admin actions go through the Admin SDK, so they do not need client rules. Setup (service account key, creating the admin document) is in `RentNearAdmin/README.md`.

---

## 14. Not included
* Real payments, push notifications, message caching, and an item edit screen (owners can create listings and deactivate them in the rules, but there is no edit UI).
* A refund path after payment: problems after payment go through chat and the claims system.
