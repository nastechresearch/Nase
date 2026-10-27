<!-- GENERATED FILE — DO NOT EDIT. Source: descriptors/capabilities.yaml. Regenerate: npm run describe -->

# Capabilities

| id | wire | mcp | kotlin | cli | summary |
| --- | --- | --- | --- | --- | --- |
| `screenshot` | screenshot | screenshot | — | screenshot | Capture the screen as a JPEG and return its size. |
| `ui_tree` | ui_tree | ui_tree | — | ui_tree, tree | Read the accessibility tree of the current screen. |
| `wait_idle` | wait_idle | wait_idle | — | wait_idle, wait | Wait for the screen to stop changing instead of guessing. |
| `tap` | tap | tap | tap | tap | Tap at screen coordinates, preferring a labelled target. |
| `swipe` | swipe | swipe | swipe | swipe | Swipe between two points, optionally with a duration. |
| `long_press` | long_press | long_press | long_press | long_press, longpress | Press and hold a point for a duration. |
| `drag` | drag | drag | drag | drag | Drag from one point to another, slowly enough to register. |
| `pinch` | pinch | pinch | pinch | pinch | Two-finger pinch to zoom, from one span to another. |
| `type` | type | type | type | type | Type into the focused field; replace by default, append on request. Password fields are refused. |
| `press` | press | key | key | press | Press a navigation or submit key. |
| `open_app` | open_app | open_app | open_app | open_app, open | Open an installed app by package name or by name; the phone resolves names locally. |
| `open_settings` | open_settings | open_settings | open_settings | open_settings, settings | Jump straight to a settings screen instead of tapping through menus. |
| `speak` | speak | speak | speak | speak | Say something on the phone. |
| `ask_user` | ask_user | ask_user | ask | ask_user, ask | Ask the owner a question and wait for the answer. |
| `confirm` | confirm | confirm | — | — | Request the owner's confirmation for a risky action. |
| `done` | done | done | done | — | Finish the task and report whether it succeeded. |
| `wait_for_request` | wait_for_request | wait_for_request | — | — | Long-poll for the next request from the owner. |
| `ping` | ping | — | — | ping | Liveness check. |
| `info` | info | — | — | — | Report app name, version and features. |
| `disconnect` | disconnect | disconnect | — | — | End the session. |
| `cancel` | cancel | — | — | — | Cancel the current operation. |
| `remember` | — | — | remember | — | Store a fact in the phone-side memory. |
| `forget` | — | — | forget | — | Delete a stored fact. |
| `recall_text` | — | — | recall_text | — | Read back previously copied text. |
| `copy_text` | — | — | copy_text | — | Copy text to the phone clipboard. |
| `schedule_task` | — | — | schedule_task | — | Schedule a task to run later on the phone. |
| `pair` | — | pair | — | — | Pair the MCP host with a phone using a one-time code. |
| `status` | — | status | — | — | Report connection, display and brain status. |

# App seed

| package | label | aliases | hidden display |
| --- | --- | --- | --- |
| `com.ubercab` | Uber | uber, rides, taxi | bounces |
| `me.lyft.android` | Lyft | lyft, rides | bounces |
| `com.airbnb.android` | Airbnb | airbnb, stay, rental | bounces |
| `com.hoteltonight.android.prod` | HotelTonight | hotel tonight, hoteltonight, hotel | bounces |
| `com.united.mobile.android` | United | united, flight, airline | bounces |
| `com.teslamotors.tesla` | Tesla | tesla, my car, charging | bounces |
| `com.google.android.apps.maps` | Google Maps | maps, google maps, navigation | bounces |
| `com.amazon.mShop.android.shopping` | Amazon | amazon, shopping | bounces |
| `com.target.ui` | Target | target, shopping | bounces |
| `com.dd.doordash` | DoorDash | doordash, food delivery | bounces |
| `com.instacart.client` | Instacart | instacart, groceries, grocery delivery | bounces |
| `com.postmates.android` | Postmates | postmates, food delivery | bounces |
| `com.resy.android` | Resy | resy, reservation, book a table | bounces |
| `com.yelp.android` | Yelp | yelp, reviews, restaurant | bounces |
| `com.zillow.android.zillowmap` | Zillow | zillow, real estate, rentals | bounces |
| `com.spotify.music` | Spotify | spotify, music, podcast | bounces |
| `com.google.android.youtube` | YouTube | youtube, video | bounces |
| `com.google.android.apps.photos` | Google Photos | photos, google photos, album | bounces |
| `com.google.android.gm` | Gmail | gmail, email, mail | bounces |
| `com.android.settings` | Settings | settings, phone settings | bounces |
| `com.android.calculator2` | Calculator | calculator, calc | bounces |
