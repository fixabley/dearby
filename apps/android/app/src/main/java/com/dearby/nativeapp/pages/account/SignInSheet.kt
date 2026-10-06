package com.dearby.nativeapp.pages.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import kotlinx.coroutines.delay

/**
 * Two-step email sign-in shown only when an action needs an account. Messages never say whether the
 * address already has an account. [codeSentAt] (epoch ms) drives the one-per-minute resend wait.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SignInSheet(
    codeStep: Boolean, busy: Boolean, email: String, message: String?, codeSentAt: Long?,
    requestCode: (String) -> Unit, verify: (String) -> Unit, changeEmail: () -> Unit, close: () -> Unit,
) {
    var address by remember { mutableStateOf(email) }
    var code by remember(codeSentAt) { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(codeSentAt) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    val wait = codeSentAt?.let { ((it + 60_000 - now + 999) / 1_000).coerceAtLeast(0) } ?: 0
    ModalBottomSheet(close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("로그인", style = MaterialTheme.typography.titleLarge)
            if (!codeStep) {
                Text("이메일로 받은 인증번호로 로그인해요.", color = Quiet, style = MaterialTheme.typography.bodyMedium)
                DearbyInlineField("이메일", address, { address = it }, editing = true, placeholder = "name@example.com",
                    modifier = Modifier.testTag("sign-in-email").semantics { contentType = ContentType.EmailAddress },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Send))
                DearbyButton({ requestCode(address) }, Modifier.fillMaxWidth(), enabled = !busy && address.isNotBlank()) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("인증번호 받기")
                }
            } else {
                Text("${email}으로 보낸 인증번호 6자리를 입력해 주세요. 5분 동안 쓸 수 있어요.", color = Quiet, style = MaterialTheme.typography.bodyMedium)
                DearbyInlineField("인증번호", code, { value -> code = value.filter(Char::isDigit).take(6) }, editing = true, placeholder = "6자리 숫자",
                    modifier = Modifier.testTag("sign-in-code").semantics { contentType = ContentType.SmsOtpCode },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done))
                DearbyButton({ verify(code) }, Modifier.fillMaxWidth(), enabled = !busy && code.length == 6) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("로그인")
                }
                DearbyOutlineButton({ requestCode(email) }, Modifier.fillMaxWidth(), enabled = !busy && wait == 0L) {
                    Text(if (wait > 0) "${wait}초 후 다시 받을 수 있어요" else "인증번호 다시 받기")
                }
                TextButton(changeEmail, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("이메일 바꾸기") }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
