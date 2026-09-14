#!/usr/bin/env python3
"""Small lexical FSD guard for this single Swift module, not a Swift parser."""
import argparse
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1] / 'Dearby'
LAYERS = ['App', 'Pages', 'Widgets', 'Features', 'Entities', 'Shared']


def code_only(source):
    return re.sub(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"', ' ', source, flags=re.S)


def check(sources, selected=()):
    declarations = {}
    cleaned = {p: code_only(s) for p, s in sources.items()}
    for path, code in cleaned.items():
        for name in re.findall(r'\b(?:struct|class|enum|protocol|typealias)\s+(\w+)', code):
            declarations.setdefault(name, set()).add(path)
    errors = []
    for path, code in cleaned.items():
        if selected and not any(path.startswith(s + '/') for s in selected):
            continue
        if re.search(r'\b(?:requestFullAccessToEvents|requestWriteOnlyAccessToEvents|requestAccess|EKAlarm|addAlarm)\b|\b(?:eventStore|store)\.save\s*\(', code):
            errors.append(f'{path}: calendar editor must not request access, save directly or add alarms')
        if path.startswith('Pages/NoticeDetail/UI/') and re.search(r'\b(?:ActivityCatalog|ActivityNotice)\b', code):
            errors.append(f'{path}: detail UI must receive ActivityDetail, not raw catalog/notice')
        parts = path.split('/')
        layer = parts[0]
        if layer not in LAYERS:
            errors.append(f'{path}: unknown layer')
            continue
        if layer not in ('App', 'Shared') and (len(parts) < 4 or parts[2] not in ('UI', 'Model', 'API')):
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
                if layer == target_layer and layer not in ('App', 'Shared') and parts[1] != target_parts[1]:
                    errors.append(f'{path}: cross-slice reference to {name} ({target})')
                value_result = (layer == 'Pages' and name == 'SaveOrganizationResult'
                                and target == 'Features/FavoriteOrganization/Model/SaveOrganizationResult.swift')
                if (layer in ('Pages', 'Widgets') and target_layer == 'Features' and not value_result) or ('UI' in parts and 'API' in target_parts):
                    errors.append(f'{path}: UI must receive values/callbacks, not {name}')
        if layer in ('Pages', 'Widgets') or (layer in ('Entities', 'Shared') and 'UI' in parts):
            if re.search(r'\b(?:UserDefaults|Bundle|FileManager|URLSession|UIApplication|openURL|MKMapItem|CLLocationManager|EventKit|EventKitUI|EKEventStore|EKEventEditViewController)\b', code):
                errors.append(f'{path}: direct storage/resource access from UI')
    return sorted(set(errors))


def self_test():
    fixture = {
        'Pages/Discovery/UI/Discovery.swift': 'struct Discovery { let card: Card }',
        'Pages/Detail/UI/Detail.swift': 'struct Detail {}',
        'Widgets/Card/UI/Card.swift': 'struct Card { let item: Item }',
        'Widgets/Other/UI/Other.swift': 'struct Other {}',
        'Features/Favorite/Model/State.swift': 'struct State {}',
        'Entities/Catalog/Model/Item.swift': 'struct Item {}',
        'Entities/Catalog/API/Provider.swift': 'struct Provider {}',
        'Entities/Catalog/UI/Label.swift': 'struct Label { let item: Item }',
    }
    fixture['Features/FavoriteOrganization/Model/SaveOrganizationResult.swift'] = 'enum SaveOrganizationResult {}'
    fixture['Features/FavoriteOrganization/API/FavoriteOrganizationsRepository.swift'] = 'protocol FavoriteOrganizationsRepository {}'
    fixture['Pages/Discovery/UI/Discovery.swift'] += '\nlet result: SaveOrganizationResult'
    assert not check(fixture)
    for raw_type in ['ActivityCatalog', 'ActivityNotice']:
        assert check({**fixture, 'Pages/NoticeDetail/UI/NoticeDetailView.swift': 'struct NoticeDetailView { let raw: ' + raw_type + ' }'})
    for forbidden in ['requestFullAccessToEvents()', 'requestWriteOnlyAccessToEvents()', 'store.save(event)', 'EKAlarm()']:
        assert check({**fixture, 'App/Editor.swift': 'struct Editor {}\n' + forbidden})
    for path, reference in [
        ('Pages/Discovery/UI/Discovery.swift', 'Detail'),
        ('Widgets/Card/UI/Card.swift', 'Discovery'),
        ('Widgets/Card/UI/Card.swift', 'Other'),
        ('Widgets/Card/UI/Card.swift', 'State'),
        ('Pages/Discovery/UI/Discovery.swift', 'State'),
        ('Pages/Discovery/UI/Discovery.swift', 'FavoriteOrganizationsRepository'),
        ('Widgets/Card/UI/Card.swift', 'SaveOrganizationResult'),
        ('Widgets/Card/UI/Card.swift', 'UserDefaults'),
        ('Pages/Detail/UI/Detail.swift', 'openURL'),
        ('Pages/Detail/UI/Detail.swift', 'UIApplication'),
        ('Pages/Detail/UI/Detail.swift', 'EKEventStore'),
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
