# RentNear – demo script (video + live)

Use two phones/emulators (Account A = owner, Account B = renter). Each row: what to show, then what to say.

| Rubric item | Where to show it | Talking point |
|---|---|---|
| Features / purpose | Home → Item → Chat → Book → Pay → QR handover → Return → Review | Peer-to-peer rental marketplace with escrow-style demo wallet, QR + photo handover, reviews, claims, debts |
| Multiple activities, navigation | Splash → Login/Register → MainActivity (fragments via Navigation component) | 3 auth activities + 1 host activity with a nav graph |
| Explicit intent | Login → MainActivity; notification tap → booking (`openFromIntent`) | `new Intent(this, MainActivity.class)` with extras `bookingId` / `openDebts` |
| Implicit intent | Item detail → "Open pickup in Maps" and "Share listing"; Wallet → "Export history (CSV)" | `ACTION_VIEW geo:`, `ACTION_SEND` with chooser |
| ViewBinding | Any screen's code | `FragmentXBinding.inflate(...)`; no `findViewById` |
| Event handling | Buttons, rating dialog, chat send, QR scan callbacks | `setOnClickListener`, ActivityResult callbacks, LiveData observers |
| Layouts | Home (list), Item detail (scroll), Booking (linear), Chat | LinearLayout/ScrollView + Material components |
| RecyclerView + adapters/ViewHolders | Home list, chat messages, wallet history, reviews, leaderboard | `ListAdapter` + `DiffUtil` + ViewHolder with ViewBinding |
| Broadcast receiver | Toggle airplane mode or battery saver while app is open → orange banner + toast. Reboot → `BootReceiver` re-schedules reminders | Dynamic `SystemEventReceiver` (registered in onStart/onStop) + manifest `BootReceiver` |
| Service | Booking (in use) → "Show countdown in notification" | Foreground `RentalTimerService` with live countdown and Stop action |
| Content provider | Friends → "Pick from contacts" (ContactsContract query); Wallet export (FileProvider) | Consumes the Contacts provider; exposes a CSV through `FileProvider` |
| WorkManager / background | Booking reminders, debt reminders (7/3/1 days), resync after boot | `ReminderScheduler`, `DebtReminderWorker`, `ResyncRemindersWorker` |
| Permissions | Camera (QR/photos), Notifications, Contacts (runtime request) | Requested at the moment of use, denial handled |
| REST / JSON | Add item → address search; Home → geocoding | Retrofit + Gson against Geoapify |
| Firebase Auth | Register / login / sign out | Email+password, verified state |
| Firestore | Listings, bookings, chats update live on the second phone | Rules enforce transitions (see README security section) |
| Realtime Database | Open a chat → header shows "Online" / "Last seen …" | `presence/{uid}` with `onDisconnect()` |
| Cloud storage for pictures | Add item with photos → URLs from Cloudinary | Unsigned upload preset, URLs saved in Firestore |
| Rotation / lifecycle | Rotate on Home and Booking detail | State lives in ViewModels, survives configuration change |
| Admin client (bonus) | JavaFX admin: users, reports, claims, debts | Separate app using Firebase Admin SDK |

## One-minute path for the live demo
1. B: register, add demo money, search, open item, chat, send offer.
2. A: accept offer. B: book. A: accept. B: pay.
3. A: before-photos + show QR. B: scan, photos, start. B: show countdown notification.
4. Toggle airplane mode on B → banner. Open chat → presence.
5. B: return (scan, photos). A: confirm return → payout. Both: review.

## Setup reminders
- Realtime Database: Firebase console → Build → Realtime Database → Create; publish `database.rules.json`; re-download `google-services.json`.
- Publish `firestore.rules`.
