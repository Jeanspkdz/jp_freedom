package com.jp.freedom

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jp.freedom.data.FocusRepository
import com.jp.freedom.domain.SavedAccessProfile
import com.jp.freedom.domain.StudyTimerController
import com.jp.freedom.enforcement.BlockedReason
import com.jp.freedom.enforcement.FocusPolicy
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessProfileFlowTest {
    @Test fun createdProfileIsSelectedEnforcedAndLockedForTheSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = FocusRepository(context)
        val controller = StudyTimerController(repository)
        val profile = SavedAccessProfile(UUID.randomUUID().toString(), "Flow test", setOf("reader.app"))
        repository.saveAccessProfile(profile)
        try {
            val chosen = repository.accessProfiles().first { it.id == profile.id }
            val session = controller.start(Duration.ofMinutes(30), chosen)!!
            val state = FocusPolicy.evaluate(LocalDateTime.now(), emptyList(), repository.settings(), session)
            val available = FocusPolicy.allowedPackages(state, "com.jp.freedom", setOf("phone.app"))
            assertTrue(state.blockedReason == BlockedReason.FOCUS_BLOCK)
            assertTrue("reader.app" in available)
            assertTrue("phone.app" in available)
            assertFalse("social.app" in available)
            assertFalse(controller.canMaintainProfile(profile.id))
            assertFalse(repository.deleteAccessProfile(profile.id))
            assertFalse(controller.start(Duration.ofMinutes(30), null as SavedAccessProfile?) != null)
            controller.stop()
            assertTrue(controller.canMaintainProfile(profile.id))
        } finally {
            repository.deleteAccessProfile(profile.id)
        }
    }
}
