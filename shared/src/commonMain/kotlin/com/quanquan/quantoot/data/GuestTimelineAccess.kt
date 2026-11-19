package com.quanquan.quantoot.data

import com.quanquan.quantoot.platform.KeyValueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal data class GuestTimelineAccess(val local: Boolean, val federated: Boolean) {
    companion object {
        suspend fun restore(preferences: KeyValueStore, instance: String): GuestTimelineAccess? =
            try {
                when (preferences.read(key(instance))) {
                    "00" -> GuestTimelineAccess(false, false)
                    "10" -> GuestTimelineAccess(true, false)
                    "01" -> GuestTimelineAccess(false, true)
                    "11" -> GuestTimelineAccess(true, true)
                    else -> null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }

        suspend fun refresh(
            preferences: KeyValueStore,
            api: MastodonApi,
            assumePublic: Boolean,
            cached: GuestTimelineAccess?,
        ): GuestTimelineAccess? {
            val server = try {
                withTimeoutOrNull(2_500) { api.serverInfo() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } ?: return null
            val access = GuestTimelineAccess(
                server.guestLocalFeedAvailable ?: cached?.local ?: assumePublic,
                server.guestFederatedFeedAvailable ?: cached?.federated ?: assumePublic,
            )
            if (access != cached) {
                try {
                    preferences.write(key(api.instance), "${if (access.local) 1 else 0}${if (access.federated) 1 else 0}")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A failed cache write should not hide freshly fetched capabilities.
                }
            }
            return access
        }

        private fun key(instance: String) = "guest_timeline_access_v1_${instance.lowercase()}"
    }
}
