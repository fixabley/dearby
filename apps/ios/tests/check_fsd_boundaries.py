#!/usr/bin/env python3
"""Small lexical FSD guard for this single Swift module, not a Swift parser."""
import argparse
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1] / 'Dearby'
LAYERS = ['App', 'Pages', 'Widgets', 'Features', 'Entities', 'Shared']


def code_only(source):
    return re.sub(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"', ' ', source, flags=re.S)


def declares_view(code):
    # Inspect declaration headers only; remove nested generic constraints before conformance.
    for header in re.findall(r'\b(?:struct|class|enum|extension)\s+\w+([^{}]*)\{', code):
        while re.search(r'<[^<>]*>', header):
            header = re.sub(r'<[^<>]*>', '', header)
        conformance = header.split('where', 1)[0].partition(':')[2]
        if re.search(r'(?:^|,)\s*(?:SwiftUI\.)?View\s*(?:,|$)', conformance):
            return True
    return False


def slice_identity(parts):
    return parts[:3] if parts[0] == 'Widgets' else parts[:2]


def check(sources, selected=()):
    declarations = {}
    cleaned = {p: code_only(s) for p, s in sources.items()}
    for path, code in cleaned.items():
        for name in re.findall(r'\b(?:struct|class|enum|protocol|typealias)\s+(\w+)', code):
            if name == "CodingKeys":  # Swift synthesizes decoding from this private nested name.
                continue
            declarations.setdefault(name, set()).add(path)
    errors = []
    for path, code in cleaned.items():
        if selected and not any(path.startswith(s + '/') for s in selected):
            continue
        read_adapter = path == 'Features/ReadCalendarBusy/API/EventKitBusyProvider.swift'
        if ((re.search(r'\brequestFullAccessToEvents\b', code) and not read_adapter)
                or re.search(r'\b(?:requestWriteOnlyAccessToEvents|requestAccess|EKAlarm|addAlarm)\b|\b(?:eventStore|store)\.(?:save|remove)\s*\(', code)):
            errors.append(f'{path}: calendar editor must not request access, save directly or add alarms')
        if path.startswith('Entities/') and '/Model/' in path and re.search(r'\b(?:SwiftData|ModelContext|ModelContainer)\b', code):
            errors.append(f'{path}: domain values must not depend on SwiftData')
        rendering = '/UI/' in path or (path.startswith('Widgets/') and declares_view(code))
        if rendering and re.search(r'\b(?:NoticeModel|OrganizationModel|BundleSnapshot|NoticeCatalog|Notice|NoticeDetailRepository|NoticeRepository|OrganizationRepository|\w+ViewModel)\b', code):
            errors.append(f'{path}: rendering UI accepts State and callbacks, not raw models/repositories/VMs')
        parts = path.split('/')
        layer = parts[0]
        if layer not in LAYERS:
            errors.append(f'{path}: unknown layer')
            continue
        if layer == 'Widgets' and (len(parts) != 4 or parts[2] in ('UI', 'Model', 'API')):
            errors.append(f'{path}: expected Widgets/Domain/Widget/file')
        if layer not in ('App', 'Shared', 'Widgets') and (len(parts) < 4 or parts[2] not in ('UI', 'Model', 'API')):
            errors.append(f'{path}: expected Layer/Slice/UI|Model|API/file')
        for name in set(re.findall(r'\b[A-Za-z_]\w*\b', code)) & declarations.keys():
            for target in declarations[name]:
                if target == path:
                    continue
                target_parts = target.split('/')
                target_layer = target_parts[0]
                if target_layer not in LAYERS:
                    errors.append(f'{path}: {name} points outside FSD layers ({target})')
                    continue
                if LAYERS.index(layer) > LAYERS.index(target_layer):
                    errors.append(f'{path}: upward reference to {name} ({target})')
                if layer == target_layer and layer not in ('App', 'Shared') and slice_identity(parts) != slice_identity(target_parts):
                    errors.append(f'{path}: cross-slice reference to {name} ({target})')
                value_result = (layer == 'Pages' and name == 'SaveOrganizationResult'
                                and target == 'Features/FavoriteOrganization/Model/SaveOrganizationResult.swift')
                if (layer in ('Pages', 'Widgets') and rendering and target_layer == 'Features' and target_parts[2] != 'UI' and not value_result) or (rendering and 'API' in target_parts):
                    errors.append(f'{path}: UI must receive values/callbacks, not {name}')
        if rendering:
            if re.search(r'\b(?:UserDefaults|Bundle|FileManager|SwiftData|ModelContext|ModelContainer|URLSession|UIApplication|openURL|MKMapItem|CLLocationManager|EventKit|EventKitUI|EKEventStore|EKEventEditViewController)\b', code):
                errors.append(f'{path}: direct storage/resource access from UI')
    return sorted(set(errors))


def self_test():
    fixture = {
        'Pages/Discovery/UI/Discovery.swift': 'struct Discovery { let card: Card }',
        'Pages/Detail/UI/Detail.swift': 'struct Detail {}',
        'Widgets/Notice/Card/Card.swift': 'struct Card: View { let item: Item }',
        'Widgets/Organization/Other/Other.swift': 'struct Other {}',
        'Features/Favorite/Model/State.swift': 'struct State {}',
        'Entities/Catalog/Model/Item.swift': 'struct Item {}',
        'Entities/Catalog/API/Provider.swift': 'struct Provider {}',
        'Entities/Catalog/UI/Label.swift': 'struct Label { let item: Item }',
    }
    fixture['Features/FavoriteOrganization/Model/SaveOrganizationResult.swift'] = 'enum SaveOrganizationResult {}'
    fixture['Features/FavoriteOrganization/API/FavoriteOrganizationsRepository.swift'] = 'protocol FavoriteOrganizationsRepository {}'
    fixture['Pages/Discovery/UI/Discovery.swift'] += '\nlet result: SaveOrganizationResult'
    fixture['Features/Favorite/UI/SaveButton.swift'] = 'struct SaveButton {}'
    fixture['Pages/Discovery/UI/Discovery.swift'] += '\nlet button: SaveButton'
    fixture['Widgets/Notice/Card/Card.swift'] += '\nlet button: SaveButton'
    assert not check(fixture)
    widget_view = 'Widgets/Notice/Card/Card.swift'
    fixture['Widgets/Notice/Card/CardState.swift'] = 'struct CardState {}'
    fixture['Widgets/Notice/Card/CardViewModel.swift'] = 'struct CardViewModel { let source: Provider }'
    fixture[widget_view] += '\nlet state: CardState'
    fixture['Widgets/Notice/Sibling/Sibling.swift'] = 'struct Sibling: View {}'
    assert not check(fixture)
    for declaration in ['struct Card: View', 'struct Card<Content: View>:\n SwiftUI.View where Content: Equatable',
                        'struct Card<T: Sequence>:\n Equatable, View where T.Element: View']:
        assert not check({**fixture, widget_view: declaration + ' { let state: CardState }'})
        for forbidden in ['CardViewModel', 'Provider', 'NoticeModel', 'UserDefaults', 'ModelContext', 'openURL', 'Sibling', 'Other']:
            assert check({**fixture, widget_view: declaration + ' { let bad: ' + forbidden + ' }'}), forbidden
    read_adapter = 'Features/ReadCalendarBusy/API/EventKitBusyProvider.swift'
    assert not check({read_adapter: 'store.requestFullAccessToEvents()'})
    for bad in ['store.save(event)', 'eventStore.remove(event)', 'store.requestWriteOnlyAccessToEvents()']:
        assert check({read_adapter: bad})
    assert check({'Shared/UI/Bad.swift': 'store.requestFullAccessToEvents()'})
    # A generic View constraint alone does not make the containing state a rendering View.
    assert not declares_view('struct GenericState<Content: View> { let content: Content }')
    for raw_type in ['NoticeCatalog', 'Notice', 'NoticeModel', 'OrganizationModel', 'BundleSnapshot', 'NoticeCardViewModel']:
        assert check({**fixture, 'Pages/NoticeDetail/UI/NoticeDetailView.swift': 'struct NoticeDetailView { let raw: ' + raw_type + ' }'})
    independent = {
        'Entities/Notice/Model/NoticeModel.swift': 'struct NoticeModel {}',
        'Entities/Organization/Model/OrganizationModel.swift': 'struct OrganizationModel {}',
        'Pages/NoticeDetail/Model/NoticeDetailState.swift': 'struct NoticeDetailState {}',
        'Features/AddToCalendar/Model/Mapper.swift': 'struct Mapper {}',
    }
    assert not check(independent)
    for path, ref in [('Entities/Notice/Model/NoticeModel.swift', 'OrganizationModel'),
                      ('Entities/Organization/Model/OrganizationModel.swift', 'NoticeModel'),
                      ('Features/AddToCalendar/Model/Mapper.swift', 'NoticeDetailState')]:
        assert check({**independent, path: independent[path] + ' let invalid: ' + ref})
    for forbidden in ['requestFullAccessToEvents()', 'requestWriteOnlyAccessToEvents()', 'store.save(event)', 'EKAlarm()']:
        assert check({**fixture, 'App/Editor.swift': 'struct Editor {}\n' + forbidden})
    for path, reference in [
        ('Pages/Discovery/UI/Discovery.swift', 'Detail'),
        ('Widgets/Notice/Card/Card.swift', 'Discovery'),
        ('Widgets/Notice/Card/Card.swift', 'Other'),
        ('Widgets/Notice/Card/Card.swift', 'State'),
        ('Pages/Discovery/UI/Discovery.swift', 'State'),
        ('Pages/Discovery/UI/Discovery.swift', 'FavoriteOrganizationsRepository'),
        ('Widgets/Notice/Card/Card.swift', 'SaveOrganizationResult'),
        ('Widgets/Notice/Card/Card.swift', 'FavoriteOrganizationsRepository'),
        ('Widgets/Notice/Card/Card.swift', 'UserDefaults'),
        ('Pages/Detail/UI/Detail.swift', 'openURL'),
        ('Pages/Detail/UI/Detail.swift', 'UIApplication'),
        ('Pages/Detail/UI/Detail.swift', 'EKEventStore'),
        ('Pages/Detail/UI/Detail.swift', 'ModelContext'),
        ('Entities/Catalog/Model/Item.swift', 'SwiftData'),
        ('Entities/Catalog/UI/Label.swift', 'MKMapItem'),
        ('Entities/Catalog/UI/Label.swift', 'Provider'),
        ('Entities/Catalog/Model/Item.swift', 'Card'),
    ]:
        assert check({**fixture, path: fixture[path] + '\nlet forbidden: ' + reference}), reference


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--slice', action='append', default=[], help='Check a specific slice during migration')
    args = parser.parse_args()
    self_test()
    sources = {str(p.relative_to(ROOT)): p.read_text() for p in ROOT.rglob('*.swift')}
    errors = check(sources, args.slice)
    if errors:
        raise SystemExit('\n'.join(errors))
    print(f'PASS: FSD lexical boundaries ({len(sources)} Swift files; scope: {args.slice or "all"}), guard fixtures')
