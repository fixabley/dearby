#!/usr/bin/env python3
"""Exercise strict lint and fail-closed tool discovery in an isolated checkout."""
from pathlib import Path
import shutil
import subprocess
import tempfile

IOS_ROOT = Path(__file__).resolve().parents[1]
VERSION = '0.65.1'
BINARY = IOS_ROOT / 'build/tools' / f'swiftlint-{VERSION}' / 'swiftlint'


def main():
    assert BINARY.is_file(), 'Run scripts/setup_swiftlint.sh first'
    with tempfile.TemporaryDirectory(prefix='dearby-swiftlint-') as temporary:
        root = Path(temporary)
        (root / 'tests').mkdir()
        shutil.copy(IOS_ROOT / 'tests/run_swiftlint.sh', root / 'tests/run_swiftlint.sh')
        shutil.copy(IOS_ROOT / '.swiftlint.yml', root / '.swiftlint.yml')
        target = root / 'build/tools' / f'swiftlint-{VERSION}' / 'swiftlint'

        def run(expected, diagnostic):
            result = subprocess.run(['bash', str(root / 'tests/run_swiftlint.sh')],
                                    cwd='/', capture_output=True, text=True)
            output = result.stdout + result.stderr
            assert result.returncode == expected, output
            assert diagnostic in output, output

        run(1, 'missing')
        target.parent.mkdir(parents=True)
        target.write_text('#!/bin/sh\necho 0.0.0\n')
        target.chmod(0o755)
        run(1, 'version mismatch')
        target.unlink()
        target.symlink_to(BINARY)
        source = root / 'Dearby/shared/lib/LintProbe.swift'
        source.parent.mkdir(parents=True)
        source.write_text('struct LintProbe {}\n')
        run(0, '0 violations')
        source.write_text('struct LintProbe {} \n')
        run(2, 'trailing_whitespace')
        source.write_text('struct LintProbe {}\n')
        test = root / 'tests/LintProbeTests.swift'
        test.write_text('struct LintProbeTests {} \n')
        run(2, 'trailing_whitespace')
        test.write_text('struct LintProbeTests {}\n')
        run(0, '0 violations')
    print('PASS: missing tool, wrong version, strict app/test violations, restored checkout; cwd-independent')


if __name__ == '__main__':
    main()
