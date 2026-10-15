package io.wid.dearby.adaptor.persistence

import org.jooq.Field
import org.jooq.impl.DSL

// 이름 중복 비교: 공백 문자를 모두 뺀다. V12 유일 인덱스(regexp_replace(name, '\s', '', 'g'))와 같은 식이어야 인덱스를 탄다
fun withoutSpaces(name: Field<String?>): Field<String> =
    DSL.field("regexp_replace({0}, '\\s', '', 'g')", String::class.java, name)
