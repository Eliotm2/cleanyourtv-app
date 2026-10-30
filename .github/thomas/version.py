#!/usr/bin/env python3
"""Fork (Thomas): number the app after the launcher it bundles: <launcher upstream version>-thomas.N.

N follows on from the app's current version when the launcher base is the same (3.2-thomas.1 → 3.2-thomas.2),
and starts at 1 with a new base (3.0-thomas.7 → 3.2-thomas.1). The versionCode goes above both sides.
The only conflict allowed in app/build.gradle.kts is the version block (both sides change it on every
upstream release). Anything else: exit 1, a person has to look.
Usage: version.py <launcher upstream version, e.g. 3.2>
"""
import re
import sys

base = sys.argv[1]
path = "app/build.gradle.kts"
text = open(path, encoding="utf-8").read()
code_re = re.compile(r"versionCode\s*=\s*(\d+)")
name_re = re.compile(r'versionName\s*=\s*"([^"]+)"')
version_line = re.compile(r"\s*(versionCode\s*=|versionName\s*=|//)")   # comments in the block too (0.4.7)

blocks = list(re.finditer(r"<<<<<<< [^\n]*\n(.*?)=======\n(.*?)>>>>>>> [^\n]*\n", text, re.S))
if len(blocks) > 1:
    sys.exit("more than one conflict in " + path)
if blocks:
    m = blocks[0]
    for line in (m.group(1) + m.group(2)).splitlines():
        if line.strip() and not version_line.match(line):
            sys.exit("a conflict outside the version lines in " + path + ": " + line.strip())
    codes = [int(c) for c in code_re.findall(m.group(0))]
    ours = name_re.search(m.group(1))
    start, end = m.start(), m.end()
else:
    found = re.search(r"[ \t]*versionCode\s*=.*\n[ \t]*versionName\s*=.*\n", text)
    if not found:
        sys.exit("no version block in " + path)
    codes = [int(c) for c in code_re.findall(found.group(0))]
    ours = name_re.search(found.group(0))
    start, end = found.start(), found.end()

current = ours.group(1) if ours else ""
cm = re.fullmatch(r"(.+)-thomas\.(\d+)", current)
n = int(cm.group(2)) + 1 if cm and cm.group(1) == base else 1
name = f"{base}-thomas.{n}"
code = max(codes) + 1
new = (f"        versionCode = {code}\n"
       f'        versionName = "{name}"   // fork (Thomas): <launcher version>-thomas.N, the launcher it bundles plus the fork\'s changes\n')
open(path, "w", encoding="utf-8").write(text[:start] + new + text[end:])
print(f"{name} ({code})")
