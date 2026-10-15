package io.wid.dearby.adaptor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

// adaptor의 하위 패키지(persistence·security·web)는 서로 참조하지 않는다. 공유가 필요하면 application에 둔다
class AdaptorPackageDependencyTest {

    // adaptor는 core(persistence)와 api(security·web)에 나뉘어 있다. 테스트는 api 모듈 폴더에서 돈다
    private val adaptorRoots = listOf("../core", ".").map { File(it, "src/main/kotlin/io/wid/dearby/adaptor") }
    private val adaptorImport = Regex("""^import io\.wid\.dearby\.adaptor\.(\w+)""", RegexOption.MULTILINE)

    @Test
    fun `adaptor 하위 패키지는 서로 참조하지 않는다`() {
        val packages = adaptorRoots.flatMap { root -> root.listFiles { file -> file.isDirectory }!!.map { root to it } }
        assertEquals(setOf("persistence", "security", "web"), packages.map { it.second.name }.toSet())
        val violations = packages.flatMap { (root, pkg) ->
            pkg.walk().filter { it.extension == "kt" }.flatMap { file ->
                adaptorImport.findAll(file.readText()).map { it.groupValues[1] }
                    .filter { it != pkg.name }
                    .map { "${file.relativeTo(root)} -> adaptor.$it" }
            }
        }
        assertEquals(emptyList(), violations)
    }
}
