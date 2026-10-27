#!/usr/bin/env python3
"""Fork (Thomas): after merging an upstream release, set app/build.gradle.kts to
<upstream>-thomas.1 with a versionCode above both sides.

The only conflict allowed in that file is the version block (both sides change it on every
release). Anything else: exit 1, a person has to look.
Usage: version.py v0.4.1
"""
import re
import sys

tag = sys.argv[1].lstrip("v")
path = "app/build.gradle.kts"
text = open(path, encoding="utf-8").read()
code_re = re.compile(r"versionCode\s*=\s*(\d+)")
version_line = re.compile(r"\s*(versionCode|versionName)\s*=")

blocks = list(re.finditer(r"<<<<<<< [^\n]*\n(.*?)=======\n(.*?)>>>>>>> [^\n]*\n", text, re.S))
if len(blocks) > 1:
    sys.exit("more than one conflict in " + path)
if blocks:
    m = blocks[0]
    for line in (m.group(1) + m.group(2)).splitlines():
        if line.strip() and not version_line.match(line):
            sys.exit("a conflict outside the version lines in " + path + ": " + line.strip())
    codes = [int(c) for c in code_re.findall(m.group(0))]
    start, end = m.start(), m.end()
else:
    codes = [int(c) for c in code_re.findall(text)][:1]
    first = re.search(r"[ \t]*versionCode\s*=.*\n[ \t]*versionName\s*=.*\n", text)
    if not first or not codes:
        sys.exit("no version block in " + path)
    start, end = first.start(), first.end()

code = max(codes) + 1
new = (f"        versionCode = {code}\n"
       f'        versionName = "{tag}-thomas.1"   // fork (Thomas): upstream {tag} plus the fork\'s own changes\n')
open(path, "w", encoding="utf-8").write(text[:start] + new + text[end:])
print(f"{tag}-thomas.1 ({code})")
