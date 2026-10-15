package io.wid.dearby

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 모듈마다 adaptor 성격의 하위 패키지(persistence·web·security·claude·http 등)는 서로 참조하지 않는다. 공유가 필요하면 application에 둔다.
// 테스트는 api 모듈 폴더에서 돈다
class AdaptorPackageDependencyTest {

    // (모듈 폴더, 그 안의 adaptor 루트 패키지)
    private val roots = listOf(
        "../core" to "io.wid.dearby.adaptor",
        "." to "io.wid.dearby.adaptor",
        "../auth" to "io.wid.dearby.auth",
        "../admin" to "io.wid.dearby.admin.adaptor",
        "../batch" to "io.wid.dearby.batch.adaptor",
    )

    @Test
    fun `adaptor 하위 패키지는 서로 참조하지 않는다`() {
        val violations = roots.flatMap { (module, rootPackage) ->
            val root = File(module, "src/main/kotlin/" + rootPackage.replace('.', '/'))
            assertTrue(root.isDirectory, "$root 없음")
            val sibling = Regex("""^import ${Regex.escape(rootPackage)}\.(\w+)""", RegexOption.MULTILINE)
            root.listFiles { file -> file.isDirectory }!!.flatMap { pkg ->
                pkg.walk().filter { it.extension == "kt" }.flatMap { file ->
                    sibling.findAll(file.readText()).map { it.groupValues[1] }
                        .filter { it != pkg.name }
                        .map { "$module/${file.relativeTo(root)} -> $rootPackage.$it" }
                }
            }
        }
        assertEquals(emptyList(), violations)
    }
}
