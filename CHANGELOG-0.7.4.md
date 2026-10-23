# MoneyBoost 0.7.4 — Embedded Native placements

Built on the 0.7.3 revenue/preloading fixes. Native is an embedded view, never a replacement for the fullscreen interstitial APIs.

## Host manifest

Use the same metadata naming convention as the other MoneyBoost formats:

```xml
<meta-data android:name="MoneyBoost_NATIVE_HOME_ID" android:value="YOUR_HOME_NATIVE_UNIT" />
<meta-data android:name="MoneyBoost_NATIVE_ALBUM_ID" android:value="YOUR_ALBUM_NATIVE_UNIT" />
<meta-data android:name="MoneyBoost_NATIVE_ACTION_ID" android:value="YOUR_ACTION_NATIVE_UNIT" />
```

The Android Java `app` demo contains all three keys with Google's official test native ID `ca-app-pub-3940256099942544/2247696110`. Missing/empty keys disable the corresponding placement. Use separate production units to distinguish reports; the sample test ID is intentionally shared.

## Java API and demo

`MoneyBoostMediationManager` exposes:

- `MoneyBoostLoadNativeAd("home" / "album" / "action")`
- `MoneyBoostIsNativeAdReady(placement)`
- `MoneyBoostShowNativeAd(placement, x, y, width, height)` — normalized Android content-view rectangle; top-left origin, values 0..1.
- `MoneyBoostHideNativeAd(placement)` — hide and retain this page's loaded ad.
- `MoneyBoostDestroyNativeAd(placement)` — release request generation, view and ad.
- `MoneyBoostGetNativeLayoutInfo()` — normalized bottom reserve, minimum height and minimum width for Unity layout.

The Java `app/src/main/java/com/freddie/frmusicsdk/MainActivity.java` demo has Load/Show Home, Load/Show Album, Load/Show Action, Hide and Destroy buttons, readiness text, and a scroll-linked slot. Scroll to make the entire slot visible. When clipped, covered by an active fullscreen/collapsible ad, or intersecting the Banner reserve, it hides. Demo slot height is 224dp. It does not use Unity to test these APIs.

## Loading, rendering and lifecycle

- Uses Google's `NativeAdPreloader.start/isAdAvailable/pollAd/destroy` with `NativeAdView` and `MediaView`. Pending inventory, replenishment, failed-request retries and expiry are owned by Google. A page holds only the polled ad it renders; no `NativeAdLoader` or custom retry timer remains.
- Loading requires completed GMA/UMP gates. Preloader registration rolls back after a start exception. Stale callbacks after destruction are ignored. Hidden Native preload inventory is released while a collapsible request is active, preserving cache capacity for Banner.
- View contains Ad attribution, SDK AdChoices, headline, MediaView, icon when supplied, and CTA when supplied. Optional body is omitted. No manual impressions, click forwarding or clickable background.
- Current embedded template requires at least 260dp width and 200dp height. If it does not fit, it hides rather than clipping assets. Headlines/CTA are limited after the supported 25/15 Unicode code point limits.
- Both pages reserve at least 80dp at the bottom, then account for actual adaptive Banner height. Native hides while a collapsible Banner is active; Banner is always brought above Native.
- Consent inventory reset and Activity teardown destroy Native ads. The optional Unity host bootstrap registers Android lifecycle callbacks only when there is no existing MoneyBoost Activity host.
- Paid callbacks preserve the two Taichi events through the existing revenue method; they do not send standard `ad_impression` manually.

## Validation

- SDK release AAR/POM and Java demo debug APK build.
- Geometry regression: normal reserve, expanded reserve, small/offscreen slots, overflow.
- Existing fullscreen, revenue/Taichi, Firebase queue and UMP regression tests.
- Unity integration has separate edit-mode checks and real Play Mode page checks with test mock cards.
- No Android device/emulator was connected: actual network fill, Google Native Ad Validator, video click behavior and device screenshots still require Android acceptance testing with the test IDs.

No Maven remote publication, Ads campaign change or production ID configuration was performed.

## Collapsible Banner reporting

- `collapsibleBanner_show`: real impression callback with `BannerAd.isCollapsible() == true`.
- `collapsibleBanner_fallback_show`: a collapsible request filled with an ordinary banner.
- Both carry `is_collapsible`, `requested_collapsible`, `ad_unit_id`, `scene`, opportunity/impression IDs and refresh index. Duplicate callbacks within the same refresh cycle are ignored.
- These are count-only events, without `value` or `currency`; standard auto-collected ad revenue and Taichi remain unchanged. Do not import these count events as Ads conversion goals.
- BI must query the custom event count/total users rather than the auto-collected ad-format dimension. New data starts after installing this SDK; historical Banner totals cannot be split. Separate collapsible revenue/eCPM is not supplied by these count events.

## Placement transitions and final frequency decisions

- Settlement Home / PK Home / Dress Again / PK / Event exits now retain the source page until the cached interstitial closes. Unavailable/rejected/failed ads release the continuation; repeated taps and duplicate terminal callbacks cannot repeat navigation.
- Scene names distinguish `settlement_view_look`, `settlement_dress_again`, `settlement_home`, `settlement_pk`, `pk_settlement_home`, `pk_settlement_event`. Fold scenes distinguish `action_menu`, `pk_matching`, `event_list`, `pk_rank`, `album`.
- No new global Collapsible Banner cooldown. The proposed 180-second cap was removed at the user's request. Existing page opportunities remain. PK Ready keeps the prior matching/ready behavior and hides on entering Loading. Fold is blocked during a transition/fullscreen and for 30 seconds after a displayed interstitial closes.
- All advertising formats use official Google Preloader APIs: interstitial 2 queued ads; rewarded/app-open 1 each; persistent Banner and collapsible Banner separate 1-ad configurations; Native 1 per page. IDs are distinct even when official test ad unit IDs match. Google's app-wide limit still applies; capacities are maxima, not guaranteed fill.
- Banner uses `BannerAdPreloader` plus `AdView.registerBannerAd`, without `AdView.loadAd` and without an independent request/retry loop. The host rotates the visible ordinary Banner after a recorded impression and 30 seconds of foreground visible time. Hidden/background/fullscreen time does not count; no inventory keeps the current creative. Replenishment remains Google-owned. Collapsible requests do not auto-expand on this timer.
- The backend eCPM setting and ad unit IDs remain unchanged. The backend Google-optimized refresh setting is not claimed to control this registered-preload display path; the 30-second visible rotation is a client policy within Google's allowed custom-refresh interval, not Google's optimized algorithm.
- Native `native_show` is once per rendered ad, `native_click` uses the SDK click callback. Both carry placement/scene/unit/request ID. Old-ad callbacks cannot report against a replacement or destroyed placement. Neither count event includes revenue. Standard automatic revenue and both Taichi events remain unchanged.
- Automated regression covers official preloader calls, inventories, failed registration, no eager Native polling, clipping, display expiry, hide/restore, 30-second ordinary Banner rotation, hidden/background pause, no-fill retention, separate fold pools, stale paid callbacks, UMP and Unity Play Mode navigation.
- Actual Android network fill, impression/click/paid delivery and Firebase DebugView must still be checked with official test ads on a connected Android device. No device was connected in this session. No production rollout or remote Maven publication occurred.

- Final IPU-priority refinement: album retains its original collapsible request even when Native is enabled. Native renders only when no collapsible request occupies the page; no forced Native-for-fold replacement. Action now has an independent Native row after two pairs of actions (or at the end of a shorter list), without replacing its fold opportunity. `MoneyBoost_NATIVE_ACTION_ID` configures it; Java demo includes Action Load/Show buttons.

The source repository has been consolidated into `/Volumes/ExMemory/SDK/MoneyBoost`; there is no separate source-fix worktree. Action Native uses the same official NativeAdPreloader and event contract (`placement=action`, `scene=native_action`) as Home/Album. Production without an Action unit ID disables the placement; development uses official test ads.

## Dedicated collapsible Banner unit and bottom-view continuity

- Host metadata `MoneyBoost_COLLAPSIBLE_BANNER_ID` selects the dedicated fold unit directly. It never falls back to `MoneyBoost_BANNER_ID`; ordinary Banner keeps its own unit. The Java demo contains the new key with an official test Banner unit.
- Both configurations still use `BannerAdPreloader.start/isAdAvailable/pollAd/destroy`. No single-load API or custom inventory/retry queue was introduced.
- Pending/failed fold rendering leaves ordinary Banner visible; a registered visible fold is shown before hiding ordinary Banner. Closing the fold restores ordinary Banner before destroying the fold renderer.
- Ordinary rotation registers a hidden candidate view before replacing the displayed view. No inventory or a registration error keeps the previous rendered ad. Old callbacks remain fenced; new impression events carry their own unit ID and are deduplicated by actual refresh cycle. Count events contain no revenue.
- Unity optionally exports `collapsibleBannerAdUnitId` from MoneyBoostNativeSettings. Blank leaves existing host metadata untouched; the SDK reads only the dedicated fold key. Release rejects official test IDs in this override.
- Real compiled-manager handoff tests cover unavailable inventory, failed show, order of view swaps/restoration, separate unit attribution, duplicate callbacks and count-only telemetry. Android transport tests inject view registration failure and verify the old Banner survives.
- This prevents application-controlled blank handoffs; it cannot guarantee a filled ad before the first successful request or during a total lack of eligible inventory. Background/fullscreen/no-ads exclusions remain.
