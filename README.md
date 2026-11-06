# RentNear (Android)

Rent items from people nearby. This is the user app; the JavaFX admin client is a separate project (RentNear Admin).

**Status: build step 5 of 8** — auth, listings, map, browse, chat with offers, bookings, wallet, QR handover and return, reminders and late fees, plus reviews, trust score, points and a leaderboard. Step 6 (claims, debts with a 10-day deadline, freeze and ban) is next.

## Stack (all free, no billing card)
Java 17, XML + Material 3, ViewBinding, MVVM (ViewModel + LiveData + Repository), Navigation component, Firebase Spark plan (Auth + Firestore).
Later steps add MapLibre + OpenFreeMap, Geoapify free plan, Cloudinary free plan, CameraX, ML Kit, ZXing, Room, WorkManager, MPAndroidChart.

## Setup
1. Open this folder in Android Studio (Koala or newer). Let it sync; it creates the Gradle wrapper jar if missing.
2. Create a Firebase project on the **Spark** plan (no card needed) at https://console.firebase.google.com.
3. Add an Android app with package name `com.rentnear.app`.
4. Add your debug SHA-1 (needed for Google sign-in): in Android Studio run `./gradlew signingReport` and copy the `debug` SHA-1 into Firebase project settings.
5. In **Authentication → Sign-in method**, enable **Email/Password** and **Google**.
6. In **Firestore Database**, create a database, then paste `firestore.rules` into the Rules tab and publish.
7. Download `google-services.json` **after** enabling Google sign-in (so it contains the OAuth web client) and put it in `app/`. It is git-ignored.
8. Run the app on a device or emulator with Google Play services.

## Step 5 (reviews, score, points, leaderboard): what to do
No new keys. **Republish `firestore.rules`** (new `reviews` rules plus score/rating updates), rebuild, and test with two accounts: finish a rental, then each side taps **Leave a review** on the completed booking.

## What works in step 5
- **Reviews:** once a booking is `completed`, the renter and the owner can each leave one review (1 to 5 stars, optional comment up to 500 characters). The review id is `bookingId_reviewerUid`, so a second one is refused.
- **Item rating:** a renter's review updates the item's average and count (shown on the item page). **See reviews** on an item lists them.
- **Trust score:** the reviewed person's score changes by (stars - 3): 5 stars +2, 4 +1, 3 0, 2 -1, 1 -2. It can go negative.
- **Points:** the reviewed person gets 2 per star; the reviewer gets 5. Levels: Bronze, Silver (150), Gold (500), Platinum (1000).
- **Profile:** shows score, points, level, the Trusted badge, and **Reviews about me**.
- **Leaderboard** (Profile > Leaderboard): top 30 by points or by trust score; banned accounts are hidden. Tap a row to read reviews about that person.
- Review lists are sorted on the phone, so no extra Firestore index is needed.

## Known gaps in step 5
- Totals are written by the app (no Cloud Functions on the free plan). The rules only accept a score, points or rating change in the same transaction as a brand-new, valid review, with the exact expected amounts, but the owner of an item can still edit its own rating fields (an older rule), and the rules are untested.
- Points for finishing rentals on time, and score penalties for late returns or damage, arrive with claims and debts in step 6.

## Step 4b (handover, return, payout): what to do
No new keys. **Republish `firestore.rules`**, run a Gradle sync (new dependencies: CameraX, ML Kit barcode scanning, ZXing, WorkManager, and `guava` for CameraX), rebuild, and test on two devices (the camera needs a real phone or an emulator with a webcam).

## What works in step 4b
- **Handover:** after payment the owner takes condition photos, then shows a QR code. The renter scans it, takes their own photos, and confirms. The booking becomes `handed_over`.
- **Return:** the owner shows a return QR code, the renter scans it, takes photos, and taps Return item. The booking becomes `returned`.
- **Confirm and payout:** the owner takes after-photos and confirms; the held money (plus any late fee) goes to the owner's wallet and the booking is `completed`. If the owner does nothing for 24 hours after the return, the renter can close the rental, and the owner can then collect the payout.
- **Late fees:** after a 15-minute grace period, half the rate per started unit, capped at the booking total, taken from the renter's wallet on return.
- **Overdue:** a handed-over booking past its end time is marked `overdue` the next time either side opens it.
- **QR security:** the secret codes live in `bookings/{id}/secret/codes`, readable only by the owner. The rules compare the scanned code with it, so a renter cannot fake a handover.
- **Reminders:** local notifications at 24 h, 3 h, 30 min and at the due time (WorkManager). Timing is approximate because Android may delay background work. Tapping one opens the booking. Android 13+ asks for notification permission.

## Known gaps in step 4b
- The rules were written without the Firebase emulator. If the console refuses them or a write is denied unexpectedly, send me the error text.
- Wallet, score and points are written by the app, so a determined user could tamper with them (a free-plan limit).
- Nothing here has been compiled yet; expect a few build errors on first sync.

## Step 4a (bookings and wallet): what to do
No new keys or services. **Republish `firestore.rules`** (new `bookings` and `wallets` rules), rebuild, and test with two accounts: A lists an item, B requests it, A accepts, B pays.

## What works in step 4a
- **Request booking** from the item page, or **Book at agreed price** from a chat once an offer is accepted (the rate is pre-filled and the rules check it against the accepted offer). Pick start and end with date and time pickers. Any part of a unit counts as a full unit; the total is rate x units. Bookings are 30 days at most.
- **Rentals tab:** switch between "I am renting" and "I am lending". Tap a booking for details.
- **Owner:** Accept or Decline a request. Accepting is refused if it overlaps another accepted booking for the same item. The owner can cancel an accepted booking.
- **Renter:** Cancel a request or an accepted booking, or **Pay from wallet** once accepted. Payment is one batch: the booking becomes `paid`, the wallet drops by the total, the amount is held on the booking as `escrow`, and a wallet history line is added.
- **Wallet** (Profile > Wallet): new accounts get ৳2000 demo credit, with +৳500 / +৳1000 / +৳2000 top-up buttons and a history list. No real money anywhere.
- **Trust tier:** items worth more than ৳15,000 can only be rented by Trusted accounts (the badge is granted by an admin in step 7; clients cannot set it). Frozen or banned accounts cannot request or accept.
- The booking states `handed_over`, `returned`, `completed`, `overdue` and `disputed` already exist in the model; they are used from step 4b onwards.

## Known gaps in step 4a
- Wallet balance is written by the app. The rules cap a top-up at ৳5000 per write and require a payment to lower the balance by exactly the booking total, but a determined user could still top up repeatedly. Production would use Cloud Functions (needs the paid Blaze plan).
- The overlap check on Accept runs in the app, because the rules cannot query other bookings. Two requests for the same dates can both exist until one is accepted.
- A booking cannot be cancelled after it is paid yet (no refunds). Problems after payment go through chat now and the claims system in step 6.
- The money held on a paid booking is released to the owner in step 4b, when the owner confirms the return.
- The new rules were written without running the Firebase emulator. If the console refuses to publish them or a write is denied unexpectedly, send me the error text.

## Step 3 (chat): what to do
No new keys or services. Just **republish `firestore.rules`** (it now covers chats, messages, blocks and reports), then rebuild. Test with two accounts on two devices or emulators: user A lists an item, user B opens it and taps **Message owner**.

## What works in step 3
- **Message owner** on the item page opens (or creates) a 1-to-1 chat for that item. The chat id is `itemId_renterId`, so each renter has one conversation per item. Owners cannot message themselves.
- **Messages:** text, photos (uploaded through Cloudinary), and price-offer cards. Updates arrive live while the conversation is open.
- **Offers:** either side can make an offer in the item's price unit. The receiver can **Accept**, **Counter** or **Decline**. A counter marks the old offer as countered and sends a new offer back. Accepting records the agreed price on the chat and shows an "Agreed price" banner; step 4 will pre-fill the booking from it.
- **Chats tab:** list of conversations with the item thumbnail, last message, time and an unread count. The bottom-nav Chats icon shows the total unread badge while the app is open.
- **Block and report** from the ⋮ menu: blocking hides the composer for both people (and Security Rules reject messages from the blocked person); unblock from the same menu. Reporting a conversation files a `reports` document for the admin app (step 7).
- Live listeners only run on the open chat and while the app is in the foreground, to protect the free Firestore quota. There are no push notifications, so messages do not arrive while the app is closed.

## Known gaps in step 3
- Messages are not cached in Room yet (step 8), so an open chat re-reads its last 50 messages each time.
- The agreed price on a chat is written by the app. Step 4 will check it against the accepted offer message before creating a booking.
- Blocking is checked by Security Rules when a message is sent; the "blocked" banner itself is checked when the chat is opened.
- Reporting an item or a user profile (not just a conversation) comes later.

## Extra setup for step 2 (all free, no card)
1. **Geoapify** (address search + walking directions): create a free project at https://myprojects.geoapify.com and copy the API key.
2. **Cloudinary** (listing photos): create a free account, note your *cloud name*, then Settings → Upload → Upload presets → add a preset with **Signing mode: Unsigned**.
3. Copy the three lines from `local.properties.example` into `local.properties` and fill them in. Rebuild after editing.
4. Republish `firestore.rules` (it now includes the `items` rules).
5. Map tiles come from OpenFreeMap and need no key.

Without the Geoapify key the map and listings still work, but address search and walking routes are off (use "Use my current location" when listing). Without Cloudinary values, publishing a listing fails with a clear message.

## What works in step 2
- **Home** has Map and Browse tabs sharing one nearby search (10 km radius, geohash range queries, one-shot reads to protect the free Firestore quota). Swipe down on Browse to refresh.
- **Map:** MapLibre with OpenFreeMap tiles, your position as a blue dot, items as teal pins; tap a pin for a preview card, then View.
- **Browse:** RecyclerView feed, sorted by nearest, cheapest (hourly and daily prices compared per hour) or top rated, with a ListView category filter.
- **Item detail:** photo strip, price, replacement value, owner, pickup address, and a Geoapify walking route and time on a small map. Message and booking buttons are disabled until steps 3 and 4.
- **List an item:** title, description, category, price per hour or day, replacement value, audience (Everyone or Friends only), pickup via address search or current location, up to 4 photos. Photos are downscaled to 1280 px JPEG before upload to Cloudinary.
- Without location permission the app falls back to a default centre (Khulna) and says so.

## Known gaps in step 2
- Friends-only items are visible only to their owner until the friends feature exists.
- Each geohash range query returns at most 30 listings, so a very dense area could hide some. Proper pagination (20 at a time) and Room caching of listings come in a later polish pass.
- Rating and review fields exist on items but stay at 0 until step 5.
- Prices use the ৳ symbol; change `Format.CURRENCY` to switch.
- Dependency versions (MapLibre 11.5.2, GeoFire 3.2.0 and others) were chosen from memory and not build-tested here. If Gradle cannot resolve one, bump it to the latest release.

## What works in step 1
- Sign up with email, name and password; a verification email is sent automatically.
- Log in; unverified email accounts are sent to the verify screen (resend / "I verified my email").
- Continue with Google (verified automatically).
- A `users/{uid}` document is created with trustScore 0, points 0, frozen/banned/trusted/pastDamage false.
- Banned accounts are signed out with a message.
- Bottom nav: Home, Chats, Rentals, Profile. Profile shows score and points and has sign out.

## Known limits (by design, free-tier choices)
- No Cloud Functions or FCM, so score, points and bans are written by the app and checked by Security Rules. A determined user could tamper with them; production would move this logic to Cloud Functions.
- Step 1 rules block clients from changing score, points, frozen, banned and trusted at all. Later steps will loosen this in a controlled way.

## Decisions
- Debt deadline: 10 days (reminders at 7, 3 and 1 days left).
- ID photo verification is replaced by an admin-granted **Trusted** badge based on rental history.
- Payments are a simulated wallet.

## Next
Step 6: damage claims, debts with a 10-day deadline, and freeze/ban.
