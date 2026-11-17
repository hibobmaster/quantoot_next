# Client correction review

The source candidate is `4ae09f0`. The accepted design and later refinements are in `design.md`; the append-only decision record is `../client-polish.tsv`.

An independent correctness review found that replying to an ancestor could append a sibling to the wrong conversation. `bf8b02b` captured the failing behavior and `4d1be53` corrected it. A fresh contract review verified the parent membership rule and intentional reuse of existing parent routes. A separate comment review led to removing a redundant Material3 opt-in from ThreadScreen.

The transport review traced iOS event loss to Ktor's engine dispatcher switch. `33a8f2a` reproduced the failure through the WebSocket response pipeline; `820a381` replaced cross-context flow emissions with channel sends. The final host run enables the same dispatcher switch for all 90 shared tests. Two native runs observed a new-post banner before the 20-second REST timer.

The final trail review used `gpt-6-luna` and checked this run's transcript, log, commit references, 90 shared test results, seven native UI scenarios, all 97 source hashes, physical-device receipts and Release signature. It found no unsupported completion claim. The earlier pending-release flag was closed after installation and version readback confirmed 0.6.1 (7).

Remaining attention items are the disclosed coverage limits: real-account write flows and Photos save permissions were not exercised. The parity comparison covers the documented restored interactions and does not claim complete Flutter-client parity.
