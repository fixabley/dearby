package com.dearby.nativeapp.shared.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/** Under the Apple·Google buttons (2026-10-25 user decision): the account data sign-in processes, with the privacy policy linked. */
@Composable fun SignInNotice(privacyUrl: String, modifier: Modifier = Modifier) = Text(
    buildAnnotatedString {
        append("로그인하면 계정 식별을 위해 Apple·Google 계정 정보(이메일 등)를 ")
        withLink(LinkAnnotation.Url(privacyUrl, TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline)))) { append("개인정보처리방침") }
        append("에 따라 처리해요.")
    },
    modifier, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
)
