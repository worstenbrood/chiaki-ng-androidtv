#!/usr/bin/env python3
"""Run inside the build container after CMake configuration; no console/credentials required."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
os.chdir(root)
proto = next((root / 'app/.cxx').glob('Debug/*/arm64-v8a/lib/protobuf'))
core = root / 'external/chiaki-ng/lib'
nanopb = root / 'external/chiaki-ng/third-party/nanopb'
with tempfile.TemporaryDirectory(prefix='chiaki-multislot-') as output:
    includes = [root / 'native/overrides/include', core / 'include', core / 'src', proto, nanopb]
    common = ['cc', '-std=gnu11', '-O1', '-g', '-pthread', '-ffunction-sections', '-fdata-sections',
              '-fsanitize=undefined', '-fno-omit-frame-pointer', '-Wl,--gc-sections']
    common += [f'-I{p}' for p in includes]
    sources = [root / 'native/tests/multislot_test.c', root / 'native/overrides/multislot.c',
               root / 'native/overrides/feedbacksender.c', core / 'src/controller.c',
               core / 'src/feedback.c', core / 'src/thread.c', core / 'src/time.c',
               proto / 'takion.pb.c', nanopb / 'pb_common.c', nanopb / 'pb_encode.c', nanopb / 'pb_decode.c']
    target = str(Path(output) / 'multislot-test')
    subprocess.run(common + ['-DCHIAKI_COOP_JOIN_TIMEOUT_MS=1000'] + [str(p) for p in sources] + ['-lm', '-o', target], check=True)
    env = dict(os.environ, UBSAN_OPTIONS='halt_on_error=1')
    subprocess.run([target], env=env, check=True, timeout=30)

    wire_target = str(Path(output) / 'multislot-wire-test')
    wire_sources = [root / 'native/tests/multislot_wire_test.c', root / 'native/overrides/takion.c',
                    core / 'src/feedback.c', core / 'src/thread.c', core / 'src/time.c']
    subprocess.run(common + [str(p) for p in wire_sources] + ['-lm', '-o', wire_target], check=True)
    subprocess.run([wire_target], env=env, check=True, timeout=10)
