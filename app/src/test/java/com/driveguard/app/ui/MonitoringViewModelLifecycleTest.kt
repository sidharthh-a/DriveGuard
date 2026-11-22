package com.driveguard.app.ui

import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.driveguard.app.DriveGuardApplication
import com.driveguard.app.data.AppDatabase
import com.driveguard.app.data.SessionDao
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Regression coverage for two session-lifecycle bugs that are invisible to the
 * pure-Kotlin unit tests:
 *
 *  1. [MonitoringViewModel.stop] releases and nulls the alert player, so a
 *     second [MonitoringViewModel.start] must create a fresh one — otherwise
 *     sound and vibration are silently dead after the first session.
 *  2. The session row is persisted on an Application-scoped scope, so clearing
 *     the ViewModel immediately after stopping (the user backs out of the app)
 *     must not discard it.
 *
 * The main dispatcher is replaced with a non-advancing test dispatcher, which
 * makes the second case deterministic: anything launched on `viewModelScope`
 * stays queued and is cancelled by [ViewModelStore.clear], exactly like a real
 * ViewModel clear would cancel work that had not started yet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MonitoringViewModelLifecycleTest {

    private val mainDispatcher = StandardTestDispatcher()
    private lateinit var app: DriveGuardApplication
    private lateinit var store: ViewModelStore
    private lateinit var lifecycleOwner: LifecycleOwner
    private lateinit var dao: SessionDao

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        app = RuntimeEnvironment.getApplication() as DriveGuardApplication
        store = ViewModelStore()
        lifecycleOwner = object : LifecycleOwner {
            private val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        dao = AppDatabase.get(app).sessionDao()
        runBlocking { dao.clearAll() }
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
        runBlocking { dao.clearAll() }
    }

    private fun newViewModel(): MonitoringViewModel =
        ViewModelProvider.create(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MonitoringViewModel(app) as T
            },
        ).get(MonitoringViewModel::class.java)

    @Test
    fun `alert player is recreated for a second monitoring session`() {
        val vm = newViewModel()
        val preview = PreviewView(app)

        vm.start(lifecycleOwner, preview)
        val firstPlayer = vm.activeAlertPlayer
        assertThat(firstPlayer).isNotNull()

        vm.stop()
        assertThat(vm.activeAlertPlayer).isNull()

        vm.start(lifecycleOwner, preview)
        val secondPlayer = vm.activeAlertPlayer
        assertThat(secondPlayer).isNotNull()
        assertThat(secondPlayer).isNotSameInstanceAs(firstPlayer)
    }

    @Test
    fun `session survives a ViewModel clear immediately after stop`() {
        val vm = newViewModel()

        vm.start(lifecycleOwner, PreviewView(app))
        vm.stop()

        // The user leaves the screen: the ViewModel and its scope are destroyed
        // before the insert has had a chance to run.
        store.clear()

        assertThat(awaitSessionCount(1)).isEqualTo(1)
    }

    /** Polls the DAO because persistence runs on a background scope. */
    private fun awaitSessionCount(expected: Int, timeoutMs: Long = 5_000): Int {
        val deadline = System.currentTimeMillis() + timeoutMs
        var count = 0
        while (System.currentTimeMillis() < deadline) {
            count = runBlocking { dao.count() }
            if (count >= expected) return count
            Thread.sleep(25)
        }
        return count
    }
}
