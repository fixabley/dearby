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
    'pages.profile': {'ProfilePage', 'ProfileState', 'ContactState', 'HistoryState', 'contactKindLabel'},
    'pages.qr': {'CardEditor', 'QrPage', 'CardEditorState', 'PublishSelectionState', 'VisibilityChoiceState'},
    'pages.login': {'LoginPage'},
    'pages.wallet': {'ImportPage', 'ImportEntryState', 'WalletPage', 'WalletEntryState', 'SendPage'},
    'widgets.card': {'CardContent', 'CardState', 'toState'},
    'features.account': {'AccountState', 'AuthRepository'},
    'features.guest': {'GuestStore'},
    'features.qr': {'QrActions'},
    'features.wallet': {'WalletRepository'},
    'entities.profile': {'model.ProfileModel', 'model.ContactModel', 'model.HistoryModel', 'api.ProfileRepository'},
    'entities.card': {'model.CardModel', 'model.CardSelectionModel', 'model.ExchangeContextModel', 'model.ReceiptModel', 'model.GuestSavedCardModel', 'model.ImportResultModel', 'model.importedIds', 'api.CardRepository'},
}

def owner(name):
    parts = name.split('.')
    return parts[0] if parts[0] in ('app', 'shared') else '.'.join(parts[:2]) if '.'.join(parts[:2]) in API else None

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
    if layer == 'shared' and re.search(r'\b(?:Profile|Card|Receipt|GuestSavedCard)Model\b', code): errors.append('shared infrastructure knows domain')
    if layer == 'app' and 'providers' not in path.parts and re.search(r'\b(?:Room\.databaseBuilder|HttpClient|ProfileRepository|CardRepository|WalletRepository|AuthRepository|TokenVault)\s*\(', code): errors.append('dependency construction belongs to app/providers')
    return errors

def self_test():
    cases = [
        ('pages/qr/Test.kt', 'import com.dearby.nativeapp.app.DearbyApp', False),
        ('pages/qr/Test.kt', 'import com.dearby.nativeapp.pages.profile.ProfileState', False),
        ('pages/qr/Test.kt', 'import com.dearby.nativeapp.entities.card.model.CardModel', False),
        ('pages/qr/Test.kt', 'import com.dearby.nativeapp.shared.storage.DearbyDao', False),
        ('pages/qr/Test.kt', 'import com.dearby.nativeapp.features.account.AuthRepository', False),
        ('pages/qr/Test.kt', 'import android.content.Intent', False),
        ('pages/qr/Test.kt', 'val x = LocalContext.current', False),
        ('pages/qr/Test.kt', 'import com.dearby.nativeapp.widgets.card.CardState', True),
        ('pages/qr/Test.kt', 'import com.dearby.nativeapp.shared.ui.Field', True),
        ('pages/qr/Test.kt', 'val x: CardEditorState? = null', True),
        ('widgets/card/Test.kt', 'import com.dearby.nativeapp.entities.card.model.CardModel', False),
        ('widgets/card/Test.kt', 'import com.dearby.nativeapp.shared.api.HttpClient', False),
        ('widgets/card/Test.kt', 'import com.dearby.nativeapp.widgets.other.CardState', False),
        ('entities/profile/Test.kt', 'import com.dearby.nativeapp.entities.card.model.CardModel', False),
        ('entities/profile/Test.kt', 'import com.dearby.nativeapp.features.account.AccountState', False),
        ('entities/profile/Test.kt', 'import com.dearby.nativeapp.shared.api.HttpClient', True),
        ('features/wallet/Test.kt', 'import com.dearby.nativeapp.entities.card.model.CardModel', True),
        ('features/wallet/Test.kt', 'import com.dearby.nativeapp.features.account.AccountState', False),
        ('features/wallet/Test.kt', 'import com.dearby.nativeapp.entities.card.api.PrivateDao', False),
        ('features/wallet/Test.kt', 'import com.dearby.nativeapp.pages.wallet.WalletEntryState', False),
        ('shared/ui/Test.kt', 'val profile: ProfileModel? = null', False),
        ('app/Test.kt', 'val client = HttpClient("", false) {}', False),
        ('app/providers/Test.kt', 'val client = HttpClient("", false) {}', True),
        ('app/Test.kt', 'import com.dearby.nativeapp.pages.wallet.WalletPage', True),
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
