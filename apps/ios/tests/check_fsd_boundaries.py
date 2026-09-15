#!/usr/bin/env python3
"""Lexical calendar/cache safety guard; AST FSD policy is in ArchitectureTests."""
import argparse
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1] / 'Dearby'
LAYERS = ['app', 'pages', 'widgets', 'features', 'entities', 'shared']


def code_only(source):
    return re.sub(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"', ' ', source, flags=re.S)


def check(sources, selected=()):
    errors = []
    for path, source in sources.items():
        code = code_only(source)
        read_adapter = path == 'features/checkCalendarOverlap/api/EventKitBusyProvider.swift'
        if ((re.search(r'\brequestFullAccessToEvents\b', code) and not read_adapter)
                or re.search(r'\b(?:requestWriteOnlyAccessToEvents|requestAccess|EKAlarm|addAlarm)\b|\b(?:eventStore|store)\.(?:save|remove)\s*\(', code)):
            errors.append(f'{path}: calendar editor must not request access, save directly or add alarms')
        if path.startswith('entities/') and '/model/' in path and re.search(r'\b(?:SwiftData|ModelContext|ModelContainer)\b', code):
            errors.append(f'{path}: domain values must not depend on SwiftData')
    return sorted(set(errors))


def directory_errors(root):
    errors = []
    for path in root.rglob('*'):
        relative = path.relative_to(root)
        if path.is_symlink():
            errors.append(f'source symlink forbidden: {relative}')
        elif path.is_dir() and relative.parts[0] != 'Assets.xcassets':
            if not re.fullmatch(r'[a-z][A-Za-z0-9]*', path.name):
                errors.append(f'{relative}: source directory must use lowerCamelCase')
    return errors


def self_test():
    for adapter in ('features/checkCalendarOverlap/api/EventKitBusyProvider.swift',):
        assert not check({adapter: 'store.requestFullAccessToEvents()'})
        for forbidden in ('store.save(event)', 'eventStore.remove(event)', 'store.requestWriteOnlyAccessToEvents()', 'EKAlarm()'):
            assert check({adapter: forbidden})
    for path in ('app/routes/Editor.swift', 'features/addToCalendar/api/Editor.swift', 'shared/ui/Bad.swift'):
        for forbidden in ('requestFullAccessToEvents()', 'requestWriteOnlyAccessToEvents()', 'requestAccess()', 'store.save(event)', 'eventStore.remove(event)', 'EKAlarm()', 'addAlarm()'):
            assert check({path: forbidden})
    for forbidden in ('SwiftData', 'ModelContext', 'ModelContainer'):
        assert check({'entities/notice/model/NoticeModel.swift': 'struct NoticeModel { let bad: ' + forbidden + ' }'})
    assert not check({'entities/notice/model/NoticeModel.swift': 'struct NoticeModel {} // SwiftData'})


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--slice', action='append', default=[], help='Check a specific slice during migration')
    args = parser.parse_args()
    self_test()
    naming_errors = directory_errors(ROOT)
    if naming_errors:
        raise SystemExit('\n'.join(naming_errors))
    sources = {str(p.relative_to(ROOT)): p.read_text() for p in ROOT.rglob('*.swift')}
    assert sources, "empty production source inventory"
    for layer in LAYERS:
        assert any(path.startswith(layer + "/") for path in sources), f"missing layer {layer}"
    errors = check(sources, args.slice)
    if errors:
        raise SystemExit('\n'.join(errors))
    print(f'PASS: calendar/cache lexical safety ({len(sources)} Swift files; scope: {args.slice or "all"}), guard fixtures')
