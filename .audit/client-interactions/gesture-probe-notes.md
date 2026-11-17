# Double-tap probe and input limits

The requested vertical dismissal, rebound, zoomed panning and horizontal paging all pass their native checks after e9adfce. A separate preservation probe using XCTest doubleTap fails before the first expected300% zoom on both the old app and the new gesture implementation. The explicit two-call tap attempt also failed.

Temporary metadata-only instrumentation measured the synthesized events: first down117930811, up117930878, second down117930878, up117930928. The gap from first up to second down is0ms, below Compose's40ms minimum. This invalidates that synthetic input as a realistic double-tap acceptance test. See doubletap-gesture-trace.txt. Root removed every temporary diagnostic state and test tag before final builds.

Root also tried a host AXe HID batch with50ms presses,100ms between taps, and2seconds between zoom/reset attempts while a native test waited for300% then100%. The test still timed out at300%; its input delivery or timing remains unverified. Do not call this a passing double-tap test or attribute it to an app defect without further evidence. The opt-in native case requires QUANTOOT_EXTERNAL_TIMED_TAPS=1 and a functioning host input driver; it is explicitly excluded from the main read-only regression selection.

The new recognizer retains platform double-tap limits and stationary zoomed taps remain undecided until touch slop. Independent source review found no introduced gesture defect. That source review is not a substitute for the uncompleted native double-tap acceptance. No product behavior was relaxed just to pass a synthetic event sequence.
