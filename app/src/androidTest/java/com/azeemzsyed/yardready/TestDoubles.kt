package com.azeemzsyed.yardready

import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import com.azeemzsyed.yardready.data.CityResult
import com.azeemzsyed.yardready.data.WeatherRepository
import com.azeemzsyed.yardready.domain.Forecast
import com.azeemzsyed.yardready.domain.ForecastSlot
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** Returns canned forecasts so UI tests never touch the network. */
class FakeWeatherRepository(
    var forecast: Forecast,
    /** Number of calls that fail with an IOException before succeeding. */
    var failuresBeforeSuccess: Int = 0,
    var cities: List<CityResult> = listOf(CityResult("Toronto", "Ontario", "CA", 43.65, -79.38)),
) : WeatherRepository {
    var forecastCalls = 0
        private set

    override suspend fun forecast(latitude: Double, longitude: Double, name: String): Forecast {
        forecastCalls++
        if (failuresBeforeSuccess > 0) {
            failuresBeforeSuccess--
            throw IOException("offline")
        }
        return forecast
    }

    override suspend fun searchCities(query: String) = cities
}

object FakeForecasts {
    private fun slots(now: Instant, tempC: Double, edit: (Int, ForecastSlot) -> ForecastSlot) = (0 until 40).map { i ->
        edit(
            i,
            ForecastSlot(
                time = now.plus(Duration.ofHours(3L * i)),
                tempC = tempC,
                precipProbability = 0.05,
                rainMm = 0.0,
                snowMm = 0.0,
                windMs = 3.0,
                condition = "Clear",
                description = "clear sky",
            ),
        )
    }

    /** Warm and dry all week: perfect mowing weather. */
    fun summer(now: Instant) = Forecast("Mississauga", ZoneOffset.ofHours(-4), slots(now, 21.0) { _, s -> s })

    /** About 8 cm of snow in the next 24 hours. */
    fun snowstorm(now: Instant) = Forecast(
        "Mississauga",
        ZoneOffset.ofHours(-5),
        slots(now, -4.0) { i, s ->
            if (i in 1..4) s.copy(snowMm = 2.0, precipProbability = 0.95, condition = "Snow", description = "snow") else s
        },
    )
}

/**
 * Room and coroutines run off the main thread, which Espresso does not track,
 * so poll until the assertion passes (or time out with the last failure).
 */
fun waitForView(view: Matcher<View>, condition: Matcher<View>, timeoutMs: Long = 5_000) {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    var lastError: Throwable? = null
    while (SystemClock.uptimeMillis() < deadline) {
        try {
            onView(view).check(matches(condition))
            return
        } catch (t: Throwable) {
            lastError = t
            Thread.sleep(100)
        }
    }
    throw lastError ?: AssertionError("Timed out waiting for view")
}

fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < deadline) {
        if (condition()) return
        Thread.sleep(100)
    }
    throw AssertionError("Condition not met within $timeoutMs ms")
}

/** Espresso's scrollTo() does not support NestedScrollView, so scroll it ourselves. */
fun nestedScrollTo(): ViewAction = object : ViewAction {
    override fun getConstraints(): Matcher<View> = allOf(
        isDescendantOfA(isAssignableFrom(NestedScrollView::class.java)),
        withEffectiveVisibility(Visibility.VISIBLE),
    )

    override fun getDescription() = "scroll to view inside a NestedScrollView"

    override fun perform(uiController: UiController, view: View) {
        view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
        uiController.loopMainThreadUntilIdle()
    }
}
