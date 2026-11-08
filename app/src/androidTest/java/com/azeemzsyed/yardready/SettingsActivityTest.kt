package com.azeemzsyed.yardready

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azeemzsyed.yardready.data.local.AppDatabase
import com.azeemzsyed.yardready.ui.settings.SettingsActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SettingsActivityTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.inMemory(context)
        ServiceLocator.database = db
        ServiceLocator.weatherRepository = FakeWeatherRepository(FakeForecasts.summer(Instant.now()))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun searchingAndChoosingACitySavesItAsTheSelectedLocation() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            waitForView(withId(R.id.currentLocation), withText("No location set yet"))
            onView(withId(R.id.searchInput)).perform(typeText("Toronto"), closeSoftKeyboard())
            onView(withId(R.id.searchButton)).perform(click())
            waitForView(withText("Toronto, Ontario, CA"), isDisplayed())
            onView(withText("Toronto, Ontario, CA")).perform(click())

            waitUntil { runBlocking { db.locationDao().getSelected() } != null }
            val selected = runBlocking { db.locationDao().getSelected() }!!
            assertEquals("Toronto", selected.name)
            assertEquals(43.65, selected.latitude, 0.001)
        }
    }
}
