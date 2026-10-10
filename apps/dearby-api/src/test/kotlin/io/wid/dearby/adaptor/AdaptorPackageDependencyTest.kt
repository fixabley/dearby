package io.wid.dearby.adaptor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

// adaptor의 하위 패키지(persistence·security·web)는 서로 참조하지 않는다. 공유가 필요하면 application에 둔다
class AdaptorPackageDependencyTest {

    private val adaptorRoot = File("src/main/kotlin/io/wid/dearby/adaptor")
    private val adaptorImport = Regex("""^import io\.wid\.dearby\.adaptor\.(\w+)""", RegexOption.MULTILINE)

    @Test
    fun `adaptor 하위 패키지는 서로 참조하지 않는다`() {
        val violations = adaptorRoot.listFiles { file -> file.isDirectory }!!.flatMap { pkg ->
            pkg.walk().filter { it.extension == "kt" }.flatMap { file ->
                adaptorImport.findAll(file.readText()).map { it.groupValues[1] }
                    .filter { it != pkg.name }
                    .map { "${file.relativeTo(adaptorRoot)} -> adaptor.$it" }
            }
        }
        assertEquals(emptyList(), violations)
    }
}
