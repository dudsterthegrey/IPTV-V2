package com.streamvault.data.epg

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EpgNameNormalizerLooseKeyTest {
    @Test
    fun `loose key drops country tag and quality words but keeps west feeds apart`() {
        assertThat(EpgNameNormalizer.looseKey("USA News 12 Brooklyn")).isEqualTo("news12brooklyn")
        assertThat(EpgNameNormalizer.looseKey("News.12.Brooklyn")).isEqualTo("news12brooklyn")
        assertThat(EpgNameNormalizer.looseKey("USA HBO Hits West*")).isEqualTo("hbohitswest")
        assertThat(EpgNameNormalizer.looseKey("HBO Hits (Pacific)")).isEqualTo("hbohitswest")
        assertThat(EpgNameNormalizer.looseKey("HBO Hits East")).isEqualTo("hbohits")
        assertThat(EpgNameNormalizer.looseKey("UK: Sky Atlantic HD")).isEqualTo("skyatlantic")
        assertThat(EpgNameNormalizer.looseKey("USA Paramount+ with SHOWTIME (East)"))
            .isEqualTo(EpgNameNormalizer.looseKey("Paramount+ with Showtime HD"))
    }
}
