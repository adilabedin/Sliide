package com.androidlearning.sliide

import android.util.TypedValue
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MainActivity launches with Theme.Sliide.Starting (the native splash). installSplashScreen() must then switch it to
 * postSplashScreenTheme (Theme.Sliide), whose window background is the in-app background, so no mismatched frame
 * appears between the splash and Compose (spec: app-branding, "Splash hands over without a flash").
 */
@RunWith(AndroidJUnit4::class)
class SplashThemeTest {
    @Test
    fun postSplashThemeIsAppliedAfterLaunch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val windowBackground = TypedValue()
                activity.theme.resolveAttribute(android.R.attr.windowBackground, windowBackground, true)

                assertEquals(R.color.window_background, windowBackground.resourceId)
                assertEquals(activity.getColor(R.color.window_background), windowBackground.data)
            }
        }
    }
}
