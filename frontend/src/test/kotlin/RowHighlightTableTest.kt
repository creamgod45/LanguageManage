package cg.creamgod45.localization.ui

import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RowHighlightTableTest {
    @Test
    fun `selected cell paints its row without expanding the actual column selection`() {
        SwingUtilities.invokeAndWait {
            val table =
                RowHighlightTable(DefaultTableModel(2, 3)).apply {
                    cellSelectionEnabled = true
                    rowSelectionAllowed = true
                    columnSelectionAllowed = true
                    selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
                }

            table.changeSelection(0, 1, false, false)

            assertTrue(table.isCellSelected(0, 0))
            assertTrue(table.isCellSelected(0, 1))
            assertTrue(table.isCellSelected(0, 2))
            assertFalse(table.isCellSelected(1, 1))
            assertEquals(1, table.selectedColumnCount)
            assertEquals(1, table.selectedColumns.single())
        }
    }

    @Test
    fun `tooltip is shown only for its configured model column`() {
        SwingUtilities.invokeAndWait {
            val table =
                RowHighlightTable(DefaultTableModel(1, 3)) { modelColumn ->
                    "Open usage locations".takeIf { modelColumn == 2 }
                }.apply {
                    setSize(225, rowHeight)
                    doLayout()
                }

            fun tooltipAt(viewColumn: Int): String? {
                val bounds = table.getCellRect(0, viewColumn, true)
                val event =
                    MouseEvent(
                        table,
                        MouseEvent.MOUSE_MOVED,
                        0L,
                        0,
                        bounds.x + bounds.width / 2,
                        bounds.y + bounds.height / 2,
                        0,
                        false,
                    )
                return table.getToolTipText(event)
            }

            assertNull(tooltipAt(0))
            assertEquals("Open usage locations", tooltipAt(2))
        }
    }

    @Test
    fun `responsive grid wraps cells when the tool window becomes narrow`() {
        SwingUtilities.invokeAndWait {
            val panel =
                ResponsiveGridPanel(4, 4).apply {
                    repeat(3) { add(JButton("Button").apply { preferredSize = java.awt.Dimension(80, 24) }) }
                }

            panel.setSize(180, 100)
            panel.doLayout()
            val narrowHeight = panel.preferredSize.height
            assertTrue(panel.getComponent(2).y > panel.getComponent(0).y)

            panel.setSize(300, 100)
            panel.doLayout()
            assertTrue(panel.components.all { it.y == panel.getComponent(0).y })
            assertTrue(panel.preferredSize.height < narrowHeight)
        }
    }
}
