package cg.creamgod45.localization.ui

import cg.creamgod45.localization.HardcodedTextCandidateDto
import cg.creamgod45.localization.HardcodedTextConfidence
import kotlin.test.Test
import kotlin.test.assertEquals

class HardcodedAnalysisExportTest {
    @Test
    fun `exports every location and safely quotes csv fields`() {
        val csv =
            HardcodedAnalysisExport.csv(
                listOf(
                    HardcodedTextCandidateDto("C:/src/a.php", 3, 8, "Hello, \"user\"", HardcodedTextConfidence.HIGH),
                    HardcodedTextCandidateDto("C:/src/b.php", 9, 2, "line one\nline two", HardcodedTextConfidence.LOW),
                ),
            )

        assertEquals(
            "Value,File path,Line,Col\r\n" +
                "\"Hello, \"\"user\"\"\",\"C:/src/a.php\",3,8\r\n" +
                "\"line one\nline two\",\"C:/src/b.php\",9,2\r\n",
            csv,
        )
    }
}
