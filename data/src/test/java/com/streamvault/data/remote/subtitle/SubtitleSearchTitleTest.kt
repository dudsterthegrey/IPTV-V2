package com.streamvault.data.remote.subtitle

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SubtitleSearchTitleTest {
    @Test
    fun `provider tags, years and episode markers are removed`() {
        assertThat(SubtitleSearchTitle.clean("New Girl")).isEqualTo("New Girl")
        assertThat(SubtitleSearchTitle.clean("EN - New Girl (2011) [MULTI-SUB]")).isEqualTo("New Girl")
        assertThat(SubtitleSearchTitle.clean("|US| New Girl S06E03 Homecoming")).isEqualTo("New Girl")
        assertThat(SubtitleSearchTitle.clean("US: New Girl")).isEqualTo("New Girl")
        assertThat(SubtitleSearchTitle.clean("New Girl - S06E03 - Homecoming")).isEqualTo("New Girl")
        assertThat(SubtitleSearchTitle.clean("New.Girl.S06E03.720p")).isEqualTo("New Girl")
        assertThat(SubtitleSearchTitle.clean("4K-EN - The Office (US)")).isEqualTo("The Office")
        assertThat(SubtitleSearchTitle.clean("[EN] New Girl FHD")).isEqualTo("New Girl")
        assertThat(SubtitleSearchTitle.clean("UK - Doctor Who 4K")).isEqualTo("Doctor Who")
        assertThat(SubtitleSearchTitle.clean("Grey's Anatomy Season 6")).isEqualTo("Grey's Anatomy")
    }

    @Test
    fun `real titles with colons, numbers and dots are kept`() {
        assertThat(SubtitleSearchTitle.clean("CSI: Miami")).isEqualTo("CSI: Miami")
        assertThat(SubtitleSearchTitle.clean("NCIS: Los Angeles")).isEqualTo("NCIS: Los Angeles")
        assertThat(SubtitleSearchTitle.clean("Blade Runner 2049")).isEqualTo("Blade Runner 2049")
        assertThat(SubtitleSearchTitle.clean("The 100")).isEqualTo("The 100")
        assertThat(SubtitleSearchTitle.clean("S.W.A.T.")).isEqualTo("S.W.A.T.")
        assertThat(SubtitleSearchTitle.clean("Mr. Robot")).isEqualTo("Mr. Robot")
    }
}
