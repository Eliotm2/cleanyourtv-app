#!/usr/bin/env python3
"""Fork (Thomas): bundle a launcher APK of the fork and pin it.

Copies the APK to the bundled asset and writes its size, SHA-256, versionCode, versionName and commit
into PinnedLauncher (core/trust/.../Pins.kt) and the expectations of LauncherPinTest. The signer is not
changed: the workflow checks the APK carries the fork's key before calling this.
Usage: pin-launcher.py <apk> <versionCode> <versionName> <commit>
"""
import hashlib
import os
import re
import shutil
import sys

apk, code, name, commit = sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4][:7]
asset = "android/platform/src/main/assets/bundled/cleanyourtv-launcher.apk"
pins = "core/trust/src/main/kotlin/tv/cleanyour/core/trust/Pins.kt"
test = "core/trust/src/test/kotlin/tv/cleanyour/core/trust/LauncherPinTest.kt"

shutil.copyfile(apk, asset)
data = open(asset, "rb").read()
size, sha = len(data), hashlib.sha256(data).hexdigest()

text = open(pins, encoding="utf-8").read()
m = re.search(r"object PinnedLauncher \{.*?\n\}", text, re.S)
if not m:
    sys.exit("no PinnedLauncher in " + pins)
block = m.group(0)
for key, value in [("UPSTREAM_COMMIT", f'"{commit}"'), ("VERSION_CODE", f"{code}L"), ("VERSION_NAME", f'"{name}"'),
                   ("SIZE", f"{size:_}L"), ("SHA256", f'"{sha}"')]:
    block, n = re.subn(rf"(const val {key} = )[^\n]+", lambda mm: mm.group(1) + value, block, count=1)
    if n != 1:
        sys.exit(f"no {key} in PinnedLauncher")
open(pins, "w", encoding="utf-8").write(text[:m.start()] + block + text[m.end():])

t = open(test, encoding="utf-8").read()
for pattern, value in [(r'assertEquals\("[^"]*", PinnedLauncher\.UPSTREAM_COMMIT\)', f'assertEquals("{commit}", PinnedLauncher.UPSTREAM_COMMIT)'),
                       (r"assertEquals\(\d+L, artifact\.versionCode\)", f"assertEquals({code}L, artifact.versionCode)"),
                       (r'assertEquals\("[^"]*", artifact\.versionName\)', f'assertEquals("{name}", artifact.versionName)')]:
    t, n = re.subn(pattern, value, t, count=1)
    if n != 1:
        sys.exit("LauncherPinTest: no match for " + pattern)
open(test, "w", encoding="utf-8").write(t)
print(f"launcher {name} ({code}) pinned: {size} bytes, {sha[:12]}, commit {commit}")
