package io.wid.dearby.adaptor.persistence

import org.jooq.Field
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

// pgvector halfvec 열: jOOQ는 타입을 모르므로(생성 코드 Any?) 글자로 쓰고 real[]로 읽는다
object VectorRows {
    fun read(column: Field<*>): Field<Array<Float?>?> = DSL.field("{0}::real[]", SQLDataType.REAL.array(), column)

    // 쓰기는 cast(? as halfvec)에 이 글자를 넘긴다
    fun text(vector: FloatArray): String = vector.joinToString(",", "[", "]")

    fun floats(value: Array<Float?>?): FloatArray? = value?.let { FloatArray(it.size) { i -> it[i] ?: 0f } }
}
