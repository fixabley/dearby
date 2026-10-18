#!/usr/bin/env python3
"""Adapted from Dearby 2362917 apps/android/scripts/check-fsd.py.
Lexical source check, not a Kotlin compiler or proof of runtime architecture.
The historical Android checker checks direction, siblings, exports and pure UI;
it does not transplant iOS's two-layer-distance rule.
"""
from pathlib import Path
import re
import argparse

PREFIX = 'com.dearby.nativeapp'
ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/java/com/dearby/nativeapp'
LAYERS = {name: i for i, name in enumerate(('app', 'pages', 'widgets', 'features', 'entities', 'shared'))}
API = {
    'widgets.activity.applyPrompt': {'ApplyConfirmationSheet'},
    'widgets.profile.profileFields': {'ProfileContactFields', 'ProfileExtraContact', 'ProfileContactKinds', 'HistoryPeriodFields', 'historyPeriod', 'ActivityInfoPicker', 'PickerItem', 'EvidenceFields', 'EvidenceRow', 'JobTagPicker', 'JobTag'},
    'widgets.activity.activityCard': {'ActivityCard'},
    'widgets.activity.programHeader': {'ProgramHeaderRow'},
    'widgets.activity.progress': {'ActivityProgressRow', 'ProgressState', 'ProgressStage', 'ProgressStatus', 'ProgressMark', 'stageIcon'},
    'widgets.card.cardContent': {'CardState', 'CardHistoryState', 'ContactState', 'CardContent', 'CardStack', 'ReceivedCardRow', 'CardComposer', 'ComposerContact', 'ComposerHistory'},
    'pages.profile': {'ProfilePage', 'ProfilePhase', 'ProfileViewState', 'ProfileFormState', 'ProfileErrors', 'HistoryDraft', 'FunnelState', 'PickerSearchState', 'VersionsState', 'HistoryFunnelPage', 'ProfileVersionsPage', 'ProfileVersionPage', 'HistoryRowState', 'VersionDetailView', 'rowState', 'view', 'periodText', 'errors', 'SetupStep', 'ProfileSetupPage'},
    'pages.cards': {'MyCardsPage', 'MyCardsState', 'MyCardsPhase', 'MyCardChoice'},
    'features.passkey': {'Passkeys'},
    'features.oauth': {'OAuthBrowser', 'OAuthVerifier'},
    'pages.account': {'SignInSheet', 'LinkAccountSheet'},
    'pages.onboarding': {'IntroPage'},
    'pages.wallet': {'WalletPage', 'WalletState', 'WalletPhase', 'WalletCardPage', 'filterWallet', 'WalletSection', 'walletSections'},
    'features.calendar': {'CalendarConflictSheet'},
    'pages.activity': {'ActivityListPage', 'ActivityListPhase', 'MyActivitiesPage', 'ActivityDetailPage', 'ActivityProgressPage', 'ApplicationReportDialog', 'ActivityState', 'ActivityListState', 'ProgramState', 'ProgramPhase', 'ProgramActivities'},
    'entities.activity': {'model.ActivityModel', 'model.ActivityPage', 'model.ScheduleModel', 'model.StageSchedule', 'model.Stage', 'model.stages', 'model.currentStage', 'model.stageBadge', 'model.ProgramSummary', 'model.ProgramPage', 'model.EligibilityItem', 'model.EligibilityGroup', 'model.EligibilityCondition', 'model.lines', 'model.instant', 'model.safeHttpsUrl', 'api.fetchActivities', 'api.fetchPrograms'},
    'entities.account': {'model.AccountSession', 'model.AccountContact', 'model.AccountHistory', 'model.ProfileJob', 'model.AccountProfile', 'model.PublishedCard', 'api.AccountClient', 'api.AccountError', 'api.AccountException', 'api.SessionStore', 'api.SessionVault', 'model.WebSignIn', 'model.ContactRules', 'model.ActivityInfoRef', 'model.ActivityInfoItem', 'model.ActivityInfoPage', 'model.EvidenceMeta', 'model.ProfileVersionSummary', 'model.ProfileVersionList', 'model.ProfileVersionDetail'},
}

def owner(name):
    parts = name.split('.')
    if parts[0] in ('app', 'shared'): return parts[0]
    key = '.'.join(parts[:3] if parts[0] == 'widgets' else parts[:2])
    return key if key in API else None

def check_source(path, text):
    expected = PREFIX + '.' + '.'.join(path.parts[:-1])
    code = re.sub(r'/\*.*?\*/|//[^\n]*|""".*?"""|"(?:\\.|[^"\\])*"', '', text, flags=re.S)
    package = re.search(r'^package\s+([\w.]+)', code, re.M)
    errors = []
    if not package or package[1] != expected: errors.append('package does not match directory')
    source = owner('.'.join(path.with_suffix('').parts))
    if source is None: return errors + ['unknown source slice']
    layer = source.split('.')[0]
    code = re.sub(r'^package[^\n]*', '', code, flags=re.M)
    rendering = layer in ('pages', 'widgets') and bool(re.search(r'@Composable\b', code))
    references = re.findall(r'\b' + re.escape(PREFIX) + r'\.([\w.*]+)', code)
    for ref in references:
        if ref.split('.')[0] in ('R', 'BuildConfig'): continue
        target = owner(ref)
        if target is None: errors.append('unknown dependency: ' + ref); continue
        target_layer = target.split('.')[0]
        if LAYERS[target_layer] < LAYERS[layer]: errors.append('upward dependency: ' + ref)
        if layer == target_layer and source != target: errors.append('cross-slice dependency: ' + ref)
        if source != target and target in API:
            exports = [target + '.' + entry for entry in API[target]]
            if not any(ref == entry or ref.startswith(entry + '.') for entry in exports): errors.append('non-public dependency: ' + ref)
        if rendering and (target_layer == 'entities' or (target_layer == 'shared' and not ref.startswith('shared.ui.')) or (target_layer == 'features' and not ref.endswith('State'))):
            errors.append('UI requires State/callbacks, not domain/I/O: ' + ref)
    if rendering and re.search(r'\b(LocalContext|SharedPreferences|getSharedPreferences|AssetManager|Intent|startActivity|\w+ViewModel|\w+Repository)\b', code): errors.append('UI directly accesses provider/OS side effect')
    # Contract "모바일 실제 연결 경계": only the activity and account clients open connections; only the account vault keeps the session
    # and MainActivity keeps only the first-launch intro flag (2026-10-14). AccountViewModel keeps the web sign-in verifier and pending
    # link token across process death while Apple's or Google's page is open (2026-10-16 "애플 로그인", 2026-10-18 "구글 로그인").
    allowed = {'entities/activity/api/ActivityClient.kt': {'HttpURLConnection'}, 'entities/account/api/AccountClient.kt': {'HttpURLConnection'},
               'entities/account/api/SessionVault.kt': {'getSharedPreferences'}, 'app/MainActivity.kt': {'getSharedPreferences'},
               'app/AccountViewModel.kt': {'SavedStateHandle'}}.get(path.as_posix(), set())
    blocked = [term for term in ('Room', 'HttpClient', 'HttpURLConnection', 'WebView', 'TokenVault', 'CalendarContract', 'SQLiteDatabase', 'getSharedPreferences', 'rememberSaveable', 'SavedStateHandle') if term not in allowed]
    if re.search(r'\b(?:' + '|'.join(blocked) + r')\b', code): errors.append('prototype must not access service or persisted state')
    return errors

def self_test():
    cases = [
        ('pages/cards/Test.kt', 'import com.dearby.nativeapp.widgets.card.cardContent.CardContent', True),
        ('pages/cards/Test.kt', 'import com.dearby.nativeapp.pages.profile.ProfileState', False),
        ('widgets/card/cardContent/Test.kt', 'import com.dearby.nativeapp.shared.ui.TimelineEntry', True),
        ('widgets/card/cardContent/Test.kt', 'import com.dearby.nativeapp.pages.wallet.WalletState', False),
        ('widgets/card/cardContent/Test.kt', 'val x = LocalContext.current', False),
        ('app/Test.kt', 'import com.dearby.nativeapp.widgets.card.cardContent.PrivateCard', False),
        ('pages/activity/Test.kt', 'import com.dearby.nativeapp.entities.activity.model.ActivityModel', False),
        ('pages/activity/Test.kt', 'import com.dearby.nativeapp.shared.storage.DearbyDao', False),
        ('pages/activity/Test.kt', 'import com.dearby.nativeapp.app.ActivityViewModel', False),
        ('pages/activity/Test.kt', 'import android.content.Intent', False),
        ('pages/activity/Test.kt', 'val x = LocalContext.current', False),
        ('pages/activity/Test.kt', 'import com.dearby.nativeapp.shared.ui.DearbyButton', True),
        ('pages/activity/Test.kt', 'val x: ActivityState? = null', True),
        ('features/calendar/Test.kt', 'import com.dearby.nativeapp.entities.activity.model.ScheduleModel', True),
        ('features/calendar/Test.kt', 'import com.dearby.nativeapp.features.application.ApplicationBrowser', False),
        ('features/calendar/Test.kt', 'import com.dearby.nativeapp.pages.activity.ActivityState', False),
        ('features/calendar/Test.kt', 'import com.dearby.nativeapp.entities.activity.model.PrivateModel', False),
        ('entities/activity/Test.kt', 'import com.dearby.nativeapp.features.calendar.CalendarConflictSheet', False),
        ('shared/ui/Test.kt', 'import com.dearby.nativeapp.entities.activity.model.ActivityModel', False),
        ('app/Test.kt', 'import com.dearby.nativeapp.pages.activity.ActivityListPage', True),
        ('shared/Test.kt', 'val x = Room.databaseBuilder()', False),
        ('app/Test.kt', 'val x = getSharedPreferences()', False),
        ('features/calendar/Test.kt', 'val x = CalendarContract.Instances', False),
        ('features/application/Test.kt', 'val x = WebView(context)', False),
        ('app/Test.kt', 'val x = rememberSaveable { true }', False),
    ]
    for filename, snippet, allowed in cases:
        path = Path(filename)
        if path.parts[0] in ('pages', 'widgets'): snippet += '\n@Composable fun Render() {}'
        errors = check_source(path, 'package ' + PREFIX + '.' + '.'.join(path.parts[:-1]) + '\n' + snippet)
        assert bool(errors) != allowed, (filename, snippet, errors)
    print(f'Boundary self-test: {len(cases)} cases passed')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    if args.self_test: self_test()
    files = sorted(ROOT.rglob('*.kt'))
    if not files: raise SystemExit('No production Kotlin source')
    errors = [f'{path.relative_to(ROOT)}: {error}' for path in files for error in check_source(path.relative_to(ROOT), path.read_text())]
    if errors: raise SystemExit('\n'.join(errors))
    print(f'FSD boundaries: {len(files)} Kotlin files passed')
