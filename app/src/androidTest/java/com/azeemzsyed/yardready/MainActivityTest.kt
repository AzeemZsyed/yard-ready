package com.azeemzsyed.yardready

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azeemzsyed.yardready.data.local.AppDatabase
import com.azeemzsyed.yardready.data.local.SavedLocation
import com.azeemzsyed.yardready.ui.history.HistoryActivity
import com.azeemzsyed.yardready.ui.main.MainActivity
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    private lateinit var db: AppDatabase
    private val now: Instant = Instant.parse("2024-06-10T12:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.inMemory(context)
        ServiceLocator.database = db
        ServiceLocator.clock = Clock.fixed(now, ZoneOffset.UTC)
    }

    @After
    fun tearDown() = db.close()

    private fun saveLocation() = runBlocking {
        db.locationDao().saveAndSelect(SavedLocation(name = "Mississauga", latitude = 43.59, longitude = -79.64))
    }

    @Test
    fun showsWelcomeScreenWhenNoLocationIsSet() {
        ServiceLocator.weatherRepository = FakeWeatherRepository(FakeForecasts.summer(now))
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForView(withId(R.id.setLocationButton), isDisplayed())
            waitForView(withId(R.id.content), withEffectiveVisibility(Visibility.GONE))
        }
    }

    @Test
    fun recommendsMowingTimeOnDryForecast() {
        saveLocation()
        ServiceLocator.weatherRepository = FakeWeatherRepository(FakeForecasts.summer(now))
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForView(withId(R.id.mowHeadline), withText(startsWith("Best time to mow")))
            waitForView(withId(R.id.snowHeadline), withText("No snow in the forecast"))
            waitForView(withId(R.id.snowAlertBanner), withEffectiveVisibility(Visibility.GONE))
        }
    }

    @Test
    fun markingLawnAsMowedUpdatesAdviceAndHistory() {
        saveLocation()
        ServiceLocator.weatherRepository = FakeWeatherRepository(FakeForecasts.summer(now))
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForView(withId(R.id.mowHeadline), withText(startsWith("Best time to mow")))
            onView(withId(R.id.markMowedButton)).perform(nestedScrollTo(), click())
            waitForView(withId(R.id.mowHeadline), withText("No need to mow yet"))
            waitForView(withId(R.id.mowLast), withText(startsWith("Last mowed")))
        }
        ActivityScenario.launch(HistoryActivity::class.java).use {
            waitForView(withText("Mowed the lawn"), isDisplayed())
            waitForView(withId(R.id.statsText), withText(containsString("mowed 1 times")))
        }
    }

    @Test
    fun heavySnowShowsAlertBannerAndClearingTime() {
        saveLocation()
        ServiceLocator.weatherRepository = FakeWeatherRepository(FakeForecasts.snowstorm(now))
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForView(withId(R.id.snowAlertBanner), isDisplayed())
            waitForView(withId(R.id.snowAlertText), withText(containsString("8 cm")))
            waitForView(withId(R.id.snowHeadline), withText(startsWith("About 8 cm of snow")))
            waitForView(withId(R.id.snowDetails), withText(containsString("Best time to clear")))
            waitForView(withId(R.id.mowHeadline), withText("Mowing season is over"))
        }
    }

    @Test
    fun networkErrorShowsRetryWhichRecovers() {
        saveLocation()
        val fake = FakeWeatherRepository(FakeForecasts.summer(now), failuresBeforeSuccess = 1)
        ServiceLocator.weatherRepository = fake
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForView(withId(R.id.errorText), withText(containsString("No internet")))
            onView(withId(R.id.retryButton)).perform(click())
            waitForView(withId(R.id.errorCard), withEffectiveVisibility(Visibility.GONE))
            waitForView(withId(R.id.mowHeadline), withText(startsWith("Best time to mow")))
            assertEquals(2, fake.forecastCalls)
        }
    }
}
