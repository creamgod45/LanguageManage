package cg.creamgod45.localization.ui

import cg.creamgod45.localization.SelectionReplacementCandidateDto
import com.intellij.ui.CheckBoxList
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionCandidateListSupportTest {
    @Test
    fun `preview focus does not reduce checked replacement files`() {
        val first = SelectionReplacementCandidateDto("first.php", 1)
        val second = SelectionReplacementCandidateDto("second.php", 2)
        val list = CheckBoxList<SelectionReplacementCandidateDto>()
        list.setItems(listOf(first, second)) { it.filePath }
        list.setItemSelected(first, true)
        list.setItemSelected(second, true)

        list.selectedIndex = 1

        assertEquals(listOf("first.php", "second.php"), checkedReplacementFilePaths(list))
    }
}
