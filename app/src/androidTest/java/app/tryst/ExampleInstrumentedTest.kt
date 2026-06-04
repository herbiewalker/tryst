package app.tryst

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Placeholder instrumented test proving the device/emulator test harness is wired. */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun usesAppContext() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("app.tryst", context.packageName)
    }
}
