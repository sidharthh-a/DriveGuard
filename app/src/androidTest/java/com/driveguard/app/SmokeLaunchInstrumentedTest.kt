package com.driveguard.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launch smoke tests: the app must start cleanly (fresh install, onboarding
 * path) and survive recreation without crashing.
 */
@RunWith(AndroidJUnit4::class)
class SmokeLaunchInstrumentedTest {

    @Test
    fun activityLaunches() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertThat(activity).isNotNull()
            }
        }
    }

    @Test
    fun activitySurvivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
            scenario.onActivity { activity ->
                assertThat(activity).isNotNull()
            }
        }
    }
}
