package es.hugoalvarezajenjo.sproutstudio.core.buildinfo

import kotlin.test.Test
import kotlin.test.assertTrue

class BuildInfoTest {
    @Test
    fun `version comes from the Gradle build, not the fallback`() {
        assertTrue(Regex("""\d+\.\d+\.\d+""").matches(BuildInfo.version), "got '${BuildInfo.version}'")
    }
}
