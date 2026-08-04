package cg.creamgod45

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IssueTemplateSchemaTest {
    private val templateDirectory = Path.of(".github", "ISSUE_TEMPLATE")
    private val yaml = Yaml(SafeConstructor(LoaderOptions()))

    @Test
    fun `issue forms have valid keys and separate English and Traditional Chinese templates`() {
        val forms =
            Files
                .list(templateDirectory)
                .use { paths ->
                    paths
                        .filter { it.name.endsWith(".yml") && it.name != "config.yml" }
                        .sorted()
                        .toList()
                }

        assertEquals(6, forms.size)
        val names = forms.map { form -> validateForm(form).getValue("name") as String }
        assertEquals(3, names.count { "(English)" in it })
        assertEquals(3, names.count { "（繁體中文）" in it })
    }

    @Test
    fun `issue template config contains separate English and Traditional Chinese manual links`() {
        val config = loadMap(templateDirectory.resolve("config.yml"))
        assertAllowedKeys(config, setOf("blank_issues_enabled", "contact_links"), "config.yml")
        assertEquals(false, config["blank_issues_enabled"])
        val links = config["contact_links"] as? List<*> ?: error("config.yml contact_links must be a list")
        assertEquals(2, links.size)
        val names =
            links.mapIndexed { index, value ->
                val link = value.asMap("config.yml contact_links[$index]")
                assertAllowedKeys(link, setOf("name", "url", "about"), "config.yml contact_links[$index]")
                link.getValue("name") as String
            }
        assertTrue(names.any { "English" in it })
        assertTrue(names.any { "繁體中文" in it })
    }

    private fun validateForm(path: Path): Map<String, Any?> {
        val form = loadMap(path)
        assertAllowedKeys(form, setOf("name", "description", "title", "labels", "assignees", "body"), path.name)
        listOf("name", "description", "title", "body").forEach { key ->
            assertTrue(form.containsKey(key), "${path.name} is missing $key")
        }
        val body = form["body"] as? List<*> ?: error("${path.name} body must be a list")
        val ids = mutableSetOf<String>()
        body.forEachIndexed { index, value ->
            val location = "${path.name} body[$index]"
            val item = value.asMap(location)
            assertAllowedKeys(item, setOf("type", "id", "attributes", "validations"), location)
            val type = item["type"] as? String ?: error("$location is missing type")
            assertTrue(type in ATTRIBUTE_KEYS, "$location has unsupported type $type")
            (item["id"] as? String)?.let { id ->
                assertTrue(id.matches(Regex("[A-Za-z0-9_-]+")), "$location has invalid id $id")
                assertTrue(ids.add(id), "${path.name} contains duplicate id $id")
            }
            val attributes = item["attributes"].asMap("$location attributes")
            assertAllowedKeys(attributes, ATTRIBUTE_KEYS.getValue(type), "$location attributes")
            item["validations"]?.asMap("$location validations")?.let { validations ->
                assertAllowedKeys(validations, setOf("required"), "$location validations")
            }
            if (type == "dropdown" || type == "checkboxes") {
                val options = attributes["options"] as? List<*> ?: error("$location options must be a list")
                assertTrue(options.isNotEmpty(), "$location options must not be empty")
                if (type == "checkboxes") {
                    options.forEachIndexed { optionIndex, option ->
                        assertAllowedKeys(
                            option.asMap("$location options[$optionIndex]"),
                            setOf("label", "required"),
                            "$location options[$optionIndex]",
                        )
                    }
                }
            }
        }
        return form
    }

    private fun loadMap(path: Path): Map<String, Any?> =
        Files.newBufferedReader(path).use { reader ->
            (yaml.load<Any?>(reader) ?: error("${path.name} is empty")).asMap(path.name)
        }

    private fun Any?.asMap(location: String): Map<String, Any?> =
        (this as? Map<*, *>)?.entries?.associate { (key, value) -> key.toString() to value }
            ?: error("$location must be a mapping")

    private fun assertAllowedKeys(
        value: Map<String, Any?>,
        allowed: Set<String>,
        location: String,
    ) {
        val invalid = value.keys - allowed
        assertTrue(invalid.isEmpty(), "$location contains unsupported keys: ${invalid.sorted()}")
    }

    private companion object {
        val ATTRIBUTE_KEYS =
            mapOf(
                "markdown" to setOf("value"),
                "input" to setOf("label", "description", "placeholder", "value"),
                "textarea" to setOf("label", "description", "placeholder", "value", "render"),
                "dropdown" to setOf("label", "description", "multiple", "options"),
                "checkboxes" to setOf("label", "description", "options"),
            )
    }
}
