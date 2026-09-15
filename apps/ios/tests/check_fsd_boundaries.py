#!/usr/bin/env python3
"""Lexical calendar/cache safety guard; AST FSD policy is in ArchitectureTests."""
import argparse
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1] / 'Dearby'
LAYERS = ['App', 'Pages', 'Widgets', 'Features', 'Entities', 'Shared']


def code_only(source):
    return re.sub(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"', ' ', source, flags=re.S)


def check(sources, selected=()):
    errors = []
    for path, source in sources.items():
        code = code_only(source)
        read_adapter = path == 'Features/CheckCalendarOverlap/API/EventKitBusyProvider.swift'
        if ((re.search(r'\brequestFullAccessToEvents\b', code) and not read_adapter)
                or re.search(r'\b(?:requestWriteOnlyAccessToEvents|requestAccess|EKAlarm|addAlarm)\b|\b(?:eventStore|store)\.(?:save|remove)\s*\(', code)):
            errors.append(f'{path}: calendar editor must not request access, save directly or add alarms')
        if path.startswith('Entities/') and '/Model/' in path and re.search(r'\b(?:SwiftData|ModelContext|ModelContainer)\b', code):
            errors.append(f'{path}: domain values must not depend on SwiftData')
    return sorted(set(errors))


def self_test():
    for adapter in ('Features/CheckCalendarOverlap/API/EventKitBusyProvider.swift',):
        assert not check({adapter: 'store.requestFullAccessToEvents()'})
        for forbidden in ('store.save(event)', 'eventStore.remove(event)', 'store.requestWriteOnlyAccessToEvents()', 'EKAlarm()'):
            assert check({adapter: forbidden})
    for path in ('App/Routes/Editor.swift', 'Features/AddToCalendar/API/Editor.swift', 'Shared/UI/Bad.swift'):
        for forbidden in ('requestFullAccessToEvents()', 'requestWriteOnlyAccessToEvents()', 'requestAccess()', 'store.save(event)', 'eventStore.remove(event)', 'EKAlarm()', 'addAlarm()'):
            assert check({path: forbidden})
    for forbidden in ('SwiftData', 'ModelContext', 'ModelContainer'):
        assert check({'Entities/Notice/Model/NoticeModel.swift': 'struct NoticeModel { let bad: ' + forbidden + ' }'})
    assert not check({'Entities/Notice/Model/NoticeModel.swift': 'struct NoticeModel {} // SwiftData'})


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--slice', action='append', default=[], help='Check a specific slice during migration')
    args = parser.parse_args()
    self_test()
    for path in ROOT.rglob('*'):
        if path.is_symlink():
            raise SystemExit(f'source symlink forbidden: {path}')
    sources = {str(p.relative_to(ROOT)): p.read_text() for p in ROOT.rglob('*.swift')}
    assert sources, "empty production source inventory"
    for layer in LAYERS:
        assert any(path.startswith(layer + "/") for path in sources), f"missing layer {layer}"
    errors = check(sources, args.slice)
    if errors:
        raise SystemExit('\n'.join(errors))
    print(f'PASS: calendar/cache lexical safety ({len(sources)} Swift files; scope: {args.slice or "all"}), guard fixtures')
