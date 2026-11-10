# RentNear (Android)

Rent items from people nearby: list something you own, chat and agree a price, book it, hand it over with a QR code and condition photos, get it back, and settle damage claims. Everything runs on free plans with no billing card, and all money is a **demo wallet**.

The admin tool is a separate JavaFX project, `RentNearAdmin` (see its README).

**Status: all 8 build steps are in place.** The code was written without being compiled or run on a device, and the Firestore rules were written without the emulator. Expect a few build errors on the first Gradle sync and possibly a rules error on first publish; send the error text and it can be fixed.

## What the app does
- **Account:** email/password (with verification) or Google sign-in; banned accounts are signed out.
- **Home:** Map and Browse tabs over one nearby search (5, 10 or 20 km), category filter, sort by distance, price or rating, 20 listings at a time, last search saved for instant and offline display.
- **Listings:** title, description, category, price per hour or day, replacement value, up to 4 photos, pickup address, audience (Everyone or **Friends only**).
- **Chat:** per-item conversations with text, photos and price offers (accept, counter, decline); block and report.
- **Bookings:** request or book at an agreed price, owner accepts, renter pays from the wallet (held until the item is back).
- **Handover and return:** owner takes condition photos and shows a QR code, renter scans it and takes photos; the same on return, with late fees after a 15-minute grace period; the owner confirms and the money is released.
- **Reminders:** local notifications before a rental is due and before a debt deadline.
- **Reviews, trust score, points, leaderboard** after each completed rental.
- **Claims and debts:** damage claims within 24 hours of the return, a 10-day debt, freeze while unpaid, ban after the deadline.
- **Friends:** add by email; friends see each other's Friends-only listings.
- **Settings:** search radius, reminders on or off, clear saved listings.

## Stack (all free)
Java 17, XML + Material 3, ViewBinding, MVVM (ViewModel + LiveData + Repository), Navigation component, Firebase Spark (Auth + Firestore), MapLibre with OpenFreeMap tiles, Geoapify free plan (address search, walking routes), Cloudinary free plan (photos), CameraX, ML Kit barcode scanning, ZXing, Room, WorkManager.

## Setup
1. Open this folder in Android Studio (Koala or newer) with a JDK between 17 and 21 for Gradle. Let it sync.
2. Create a Firebase project on the **Spark** plan at https://console.firebase.google.com and add an Android app with package name `com.rentnear.app`.
3. Add your debug SHA-1 (needed for Google sign-in): run `./gradlew signingReport` and copy the `debug` SHA-1 into the Firebase project settings.
4. Authentication > Sign-in method: enable **Email/Password** and **Google**.
5. Firestore Database: create a database, then paste `firestore.rules` into the Rules tab and publish.
6. Download `google-services.json` after enabling Google sign-in and put it in `app/` (git-ignored).
7. **Geoapify** (address search, walking routes): free project at https://myprojects.geoapify.com, copy the API key.
8. **Cloudinary** (photos): free account, note the *cloud name*, then Settings > Upload > Upload presets > add a preset with **Signing mode: Unsigned**.
9. Copy the three lines from `local.properties.example` into `local.properties` and fill them in. Rebuild after editing.
10. Run on a device or emulator with Google Play services (the camera features need a real camera or an emulator webcam).

Without the Geoapify key the map and listings still work, but address search and walking routes are off. Without the Cloudinary values, publishing a listing or sending a photo fails with a clear message.

## Try it with two accounts
1. Account A lists an item. Account B opens it, messages A, and requests a booking.
2. A accepts; B pays from the wallet (**Profile > Wallet** has demo top-ups).
3. A takes condition photos and shows the handover QR; B scans it, takes photos and confirms.
4. At the end, A shows the return QR; B scans it and returns; A takes photos and confirms. Both leave reviews.
5. To test claims: after a return, A taps **Report damage** (within 24 hours); B accepts, then pays under **Profile > Debts**.
6. To test the admin: follow `RentNearAdmin/README.md`.

## Firestore indexes
Every query in the app uses a single field or equality filters only, so no composite index has to be created. If the console ever prints an index link in a Logcat error, open it and press Create (free).

## Security model and honest limits
There are no Cloud Functions (they need the paid Blaze plan) and no push notifications, so the **app writes** wallet balances, trust scores, points and debt records, and the **Firestore rules check each write**: allowed state transitions, exact amounts (payments, late fees, payouts, score and point changes), who may write what, one review per booking, and the 10-day deadline. A determined user who rebuilds the app could still tamper with some values; the weak spots are:
- **Demo wallet:** top-ups are capped per write (৳5000) but not in total. It is demo money, so this is by design.
- **Friends-only listings** are hidden in the app, but the rules let any signed-in user read listings, because a rule cannot be proven for a range query. Someone reading Firestore directly could see them, including the pickup address.
- **Scores and points** change only together with a valid new review or debt, by exact amounts, but the writer is the client.
- **Bans and freezes** take effect when the app next reads the profile. A debtor who never opens the app is banned when the owner taps **Ban** on the overdue debt or an admin runs the sweep in the admin client.
- **Reminders** use WorkManager, so their timing is approximate.
- Production would move wallet, score, debt and ban logic into Cloud Functions.

## Decisions
- Debt deadline: 10 days (reminders at 7, 3 and 1 days left). Score penalty 20 for a debt, 10 back on repayment.
- Late fee: half the rate per started unit after a 15-minute grace period, capped at the booking total.
- ID verification is replaced by an admin-granted **Trusted** badge (needed to rent items worth more than ৳15,000).
- Currency symbol ৳ (`Format.CURRENCY`).

## Not included
- Real payments, push notifications, message history caching, and an item edit screen (owners can currently create and, in the rules, deactivate listings, but there is no edit UI).

# Details by build step
The sections below are the notes written as each step was built.

### Step 7 (admin client): what to do
**Republish `firestore.rules`**, rebuild the app, then follow `RentNearAdmin/README.md` to set up the admin client (service account key, `admins/{uid}` document).

### What changed in the app for step 7
- **Profile:** **Request Trusted badge** (creates `trustRequests/{uid}`; the admin approves or rejects), and a banner showing a warning from an admin.
- **Rules:** `trustRequests`; items removed by an admin cannot be edited by the owner. `admins` stays closed to the app.
- Admin actions (warn, freeze, ban, upheld claims, removed listings) are written through the Admin SDK, so they do not need client rules.

### Step 6 (claims, debts, freeze, ban): what to do
No new keys. **Republish `firestore.rules`**, rebuild, and test with two accounts after a finished rental: the owner taps **Report damage** on the returned booking, the renter opens **Profile > Claims** and accepts, then pays under **Profile > Debts**.

### What works in step 6
- **File a claim:** the owner can claim up to the item's replacement value within 24 hours of the return, with a description. The booking's handover and return photos stay as evidence. One claim per booking.
- **Renter's choices:** accept (debt created), dispute (goes to an admin in step 7), or offer a smaller amount. The owner then accepts the offer (debt for that amount) or rejects it (dispute). The owner can withdraw a claim until the renter responds.
- **Debt:** created in the same write as the acceptance. It is due **10 days** later, never exceeds the declared value, **freezes** the renter, costs **20 trust score** and sets the past-damage flag. A frozen account cannot list items, request bookings, accept requests or pay for a booking, but can still return items and decline requests.
- **Repay:** Profile > Debts > Pay takes the amount from the demo wallet, unfreezes the account when it was the last open debt, and gives back 10 trust score. The owner then taps **Collect** to move the money to their wallet.
- **Reminders:** local notifications 7, 3 and 1 days before the deadline (timing approximate); tapping one opens Debts.
- **Ban:** after the deadline the account is banned. The app does this itself when the debtor next opens it (and signs out), and the person owed can tap **Ban account** on an overdue debt. The rules allow a ban only when the debt is open and past due.
- Disputed claims wait for an admin; the admin client (step 7) will uphold or reject them.

### Known gaps in step 6
- Without Cloud Functions, nothing bans a debtor who never opens the app until the owner taps Ban or the admin sweep (step 7) runs.
- Score recovery is only +10 on repayment plus whatever reviews add later.
- Rules are untested on the emulator; send the error text if the console rejects them.

### Step 5 (reviews, score, points, leaderboard): what to do
No new keys. **Republish `firestore.rules`** (new `reviews` rules plus score/rating updates), rebuild, and test with two accounts: finish a rental, then each side taps **Leave a review** on the completed booking.

### What works in step 5
- **Reviews:** once a booking is `completed`, the renter and the owner can each leave one review (1 to 5 stars, optional comment up to 500 characters). The review id is `bookingId_reviewerUid`, so a second one is refused.
- **Item rating:** a renter's review updates the item's average and count (shown on the item page). **See reviews** on an item lists them.
- **Trust score:** the reviewed person's score changes by (stars - 3): 5 stars +2, 4 +1, 3 0, 2 -1, 1 -2. It can go negative.
- **Points:** the reviewed person gets 2 per star; the reviewer gets 5. Levels: Bronze, Silver (150), Gold (500), Platinum (1000).
- **Profile:** shows score, points, level, the Trusted badge, and **Reviews about me**.
- **Leaderboard** (Profile > Leaderboard): top 30 by points or by trust score; banned accounts are hidden. Tap a row to read reviews about that person.
- Review lists are sorted on the phone, so no extra Firestore index is needed.

### Known gaps in step 5
- Totals are written by the app (no Cloud Functions on the free plan). The rules only accept a score, points or rating change in the same transaction as a brand-new, valid review, with the exact expected amounts, but the owner of an item can still edit its own rating fields (an older rule), and the rules are untested.
- Points for finishing rentals on time, and score penalties for late returns or damage, arrive with claims and debts in step 6.

### Step 4b (handover, return, payout): what to do
No new keys. **Republish `firestore.rules`**, run a Gradle sync (new dependencies: CameraX, ML Kit barcode scanning, ZXing, WorkManager, and `guava` for CameraX), rebuild, and test on two devices (the camera needs a real phone or an emulator with a webcam).

### What works in step 4b
- **Handover:** after payment the owner takes condition photos, then shows a QR code. The renter scans it, takes their own photos, and confirms. The booking becomes `handed_over`.
- **Return:** the owner shows a return QR code, the renter scans it, takes photos, and taps Return item. The booking becomes `returned`.
- **Confirm and payout:** the owner takes after-photos and confirms; the held money (plus any late fee) goes to the owner's wallet and the booking is `completed`. If the owner does nothing for 24 hours after the return, the renter can close the rental, and the owner can then collect the payout.
- **Late fees:** after a 15-minute grace period, half the rate per started unit, capped at the booking total, taken from the renter's wallet on return.
- **Overdue:** a handed-over booking past its end time is marked `overdue` the next time either side opens it.
- **QR security:** the secret codes live in `bookings/{id}/secret/codes`, readable only by the owner. The rules compare the scanned code with it, so a renter cannot fake a handover.
- **Reminders:** local notifications at 24 h, 3 h, 30 min and at the due time (WorkManager). Timing is approximate because Android may delay background work. Tapping one opens the booking. Android 13+ asks for notification permission.

### Known gaps in step 4b
- The rules were written without the Firebase emulator. If the console refuses them or a write is denied unexpectedly, send me the error text.
- Wallet, score and points are written by the app, so a determined user could tamper with them (a free-plan limit).
- Nothing here has been compiled yet; expect a few build errors on first sync.

### Step 4a (bookings and wallet): what to do
No new keys or services. **Republish `firestore.rules`** (new `bookings` and `wallets` rules), rebuild, and test with two accounts: A lists an item, B requests it, A accepts, B pays.

### What works in step 4a
- **Request booking** from the item page, or **Book at agreed price** from a chat once an offer is accepted (the rate is pre-filled and the rules check it against the accepted offer). Pick start and end with date and time pickers. Any part of a unit counts as a full unit; the total is rate x units. Bookings are 30 days at most.
- **Rentals tab:** switch between "I am renting" and "I am lending". Tap a booking for details.
- **Owner:** Accept or Decline a request. Accepting is refused if it overlaps another accepted booking for the same item. The owner can cancel an accepted booking.
- **Renter:** Cancel a request or an accepted booking, or **Pay from wallet** once accepted. Payment is one batch: the booking becomes `paid`, the wallet drops by the total, the amount is held on the booking as `escrow`, and a wallet history line is added.
- **Wallet** (Profile > Wallet): new accounts get ৳2000 demo credit, with +৳500 / +৳1000 / +৳2000 top-up buttons and a history list. No real money anywhere.
- **Trust tier:** items worth more than ৳15,000 can only be rented by Trusted accounts (the badge is granted by an admin in step 7; clients cannot set it). Frozen or banned accounts cannot request or accept.
- The booking states `handed_over`, `returned`, `completed`, `overdue` and `disputed` already exist in the model; they are used from step 4b onwards.

### Known gaps in step 4a
- Wallet balance is written by the app. The rules cap a top-up at ৳5000 per write and require a payment to lower the balance by exactly the booking total, but a determined user could still top up repeatedly. Production would use Cloud Functions (needs the paid Blaze plan).
- The overlap check on Accept runs in the app, because the rules cannot query other bookings. Two requests for the same dates can both exist until one is accepted.
- A booking cannot be cancelled after it is paid yet (no refunds). Problems after payment go through chat now and the claims system in step 6.
- The money held on a paid booking is released to the owner in step 4b, when the owner confirms the return.
- The new rules were written without running the Firebase emulator. If the console refuses to publish them or a write is denied unexpectedly, send me the error text.

### Step 3 (chat): what to do
No new keys or services. Just **republish `firestore.rules`** (it now covers chats, messages, blocks and reports), then rebuild. Test with two accounts on two devices or emulators: user A lists an item, user B opens it and taps **Message owner**.

### What works in step 3
- **Message owner** on the item page opens (or creates) a 1-to-1 chat for that item. The chat id is `itemId_renterId`, so each renter has one conversation per item. Owners cannot message themselves.
- **Messages:** text, photos (uploaded through Cloudinary), and price-offer cards. Updates arrive live while the conversation is open.
- **Offers:** either side can make an offer in the item's price unit. The receiver can **Accept**, **Counter** or **Decline**. A counter marks the old offer as countered and sends a new offer back. Accepting records the agreed price on the chat and shows an "Agreed price" banner; step 4 will pre-fill the booking from it.
- **Chats tab:** list of conversations with the item thumbnail, last message, time and an unread count. The bottom-nav Chats icon shows the total unread badge while the app is open.
- **Block and report** from the ⋮ menu: blocking hides the composer for both people (and Security Rules reject messages from the blocked person); unblock from the same menu. Reporting a conversation files a `reports` document for the admin app (step 7).
- Live listeners only run on the open chat and while the app is in the foreground, to protect the free Firestore quota. There are no push notifications, so messages do not arrive while the app is closed.

### Known gaps in step 3
- Messages are not cached in Room yet (step 8), so an open chat re-reads its last 50 messages each time.
- The agreed price on a chat is written by the app. Step 4 will check it against the accepted offer message before creating a booking.
- Blocking is checked by Security Rules when a message is sent; the "blocked" banner itself is checked when the chat is opened.
- Reporting an item or a user profile (not just a conversation) comes later.

### Extra setup for step 2 (all free, no card)
1. **Geoapify** (address search + walking directions): create a free project at https://myprojects.geoapify.com and copy the API key.
2. **Cloudinary** (listing photos): create a free account, note your *cloud name*, then Settings → Upload → Upload presets → add a preset with **Signing mode: Unsigned**.
3. Copy the three lines from `local.properties.example` into `local.properties` and fill them in. Rebuild after editing.
4. Republish `firestore.rules` (it now includes the `items` rules).
5. Map tiles come from OpenFreeMap and need no key.

Without the Geoapify key the map and listings still work, but address search and walking routes are off (use "Use my current location" when listing). Without Cloudinary values, publishing a listing fails with a clear message.

### What works in step 2
- **Home** has Map and Browse tabs sharing one nearby search (10 km radius, geohash range queries, one-shot reads to protect the free Firestore quota). Swipe down on Browse to refresh.
- **Map:** MapLibre with OpenFreeMap tiles, your position as a blue dot, items as teal pins; tap a pin for a preview card, then View.
- **Browse:** RecyclerView feed, sorted by nearest, cheapest (hourly and daily prices compared per hour) or top rated, with a ListView category filter.
- **Item detail:** photo strip, price, replacement value, owner, pickup address, and a Geoapify walking route and time on a small map. Message and booking buttons are disabled until steps 3 and 4.
- **List an item:** title, description, category, price per hour or day, replacement value, audience (Everyone or Friends only), pickup via address search or current location, up to 4 photos. Photos are downscaled to 1280 px JPEG before upload to Cloudinary.
- Without location permission the app falls back to a default centre (Khulna) and says so.

### Known gaps in step 2
- Friends-only items are visible only to their owner until the friends feature exists.
- Each geohash range query returns at most 30 listings, so a very dense area could hide some. Proper pagination (20 at a time) and Room caching of listings come in a later polish pass.
- Rating and review fields exist on items but stay at 0 until step 5.
- Prices use the ৳ symbol; change `Format.CURRENCY` to switch.
- Dependency versions (MapLibre 11.5.2, GeoFire 3.2.0 and others) were chosen from memory and not build-tested here. If Gradle cannot resolve one, bump it to the latest release.

### What works in step 1
- Sign up with email, name and password; a verification email is sent automatically.
- Log in; unverified email accounts are sent to the verify screen (resend / "I verified my email").
- Continue with Google (verified automatically).
- A `users/{uid}` document is created with trustScore 0, points 0, frozen/banned/trusted/pastDamage false.
- Banned accounts are signed out with a message.
- Bottom nav: Home, Chats, Rentals, Profile. Profile shows score and points and has sign out.


## Step 9 – demo components
Broadcast receivers (`SystemEventReceiver`, `BootReceiver`), foreground service (`RentalTimerService`), Contacts and FileProvider content providers, maps/share intents, Realtime Database presence (`database.rules.json`). See `DEMO.md` for the rubric mapping. Not compiled in the sandbox.
