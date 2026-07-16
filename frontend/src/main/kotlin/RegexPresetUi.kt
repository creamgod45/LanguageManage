package cg.creamgod45

import cg.creamgod45.LanguageManagerBundle.message
import javax.swing.JButton
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

internal data class RegexPreset(
    val group: String,
    val name: String,
    val patterns: List<String>,
)

internal object RegexPresetUi {
    val presets =
        listOf(
            RegexPreset("PHP", "Laravel", listOf("""(?:__|trans|trans_choice)\(\s*(?<quote>[\"'])(?<key>[^\r\n]{1,256}?)\k<quote>""")),
            RegexPreset("PHP", "Symfony", listOf("""(?:->|\.)trans\(\s*(?<quote>[\"'])(?<key>[^\r\n]{1,256}?)\k<quote>""")),
            RegexPreset("PHP", "webman", listOf("""\btrans\(\s*(?<quote>[\"'])(?<key>[^\r\n]{1,256}?)\k<quote>""")),
            RegexPreset(
                "PHP",
                "Laminas / Zend",
                listOf("""(?:->translate|->translatePlural)\(\s*(?<quote>[\"'])(?<key>[^\r\n]{1,256}?)\k<quote>"""),
            ),
            RegexPreset("PHP", "CodeIgniter", listOf("""(?:\blang|->lang->line)\(\s*(?<quote>[\"'])(?<key>[^\r\n]{1,256}?)\k<quote>""")),
            RegexPreset(
                "PHP",
                "CakePHP",
                listOf(
                    """(?:__|__n)\(\s*(?<quote>[\"'])(?<key>[^\r\n]{1,256}?)\k<quote>""",
                    """(?:__d|__dn)\(\s*[\"'][^\"']+[\"']\s*,\s*(?<quote>[\"'])(?<key>[^\r\n]{1,256}?)\k<quote>""",
                ),
            ),
            RegexPreset(
                "PHP",
                "Yii",
                listOf("""(?:\\?Yii)::t\(\s*[\"'][^\"']+[\"']\s*,\s*(?<quote>[\"'])(?<key>[^\r\n]{1,256}?)\k<quote>"""),
            ),
            RegexPreset("PHP", "Phalcon", listOf("""(?:->|\.)_(?:\s*)\(\s*(?<quote>[\"'])(?<key>[^\r\n]{1,256}?)\k<quote>""")),
            RegexPreset("PHP", "FuelPHP", listOf("""\bLang::get\(\s*(?<quote>[\"'])(?<key>[^\r\n]{1,256}?)\k<quote>""")),
            RegexPreset(
                "PHP",
                "Slim / Pixie / custom",
                listOf("""(?:__|trans|translate)\(\s*(?<quote>[\"'])(?<key>[^\r\n]{1,256}?)\k<quote>"""),
            ),
            RegexPreset(
                "Java / Kotlin",
                "Spring MessageSource",
                listOf("""\bgetMessage\(\s*(?<quote>[\"'])(?<key>[^\r\n]{1,256}?)\k<quote>"""),
            ),
            RegexPreset("Java / Kotlin", "ResourceBundle", listOf("""\bgetString\(\s*(?<quote>[\"'])(?<key>[^\r\n]{1,256}?)\k<quote>""")),
            RegexPreset(
                "JetBrains",
                "IntelliJ Platform Plugin",
                listOf("""(?:message|messagePointer|lazyMessage)\(\s*(?<quote>[\"'])(?<key>[^\r\n]{1,256}?)\k<quote>"""),
            ),
        )

    fun button(addPatterns: (List<String>) -> Unit): JButton {
        val menu = JPopupMenu()
        presets.groupBy(RegexPreset::group).forEach { (group, items) ->
            menu.add(
                JMenu(group).apply {
                    items.forEach { preset -> add(JMenuItem(preset.name).apply { addActionListener { addPatterns(preset.patterns) } }) }
                },
            )
        }
        return JButton(message("settings.regex.recommended")).apply { addActionListener { menu.show(this, 0, height) } }
    }
}
