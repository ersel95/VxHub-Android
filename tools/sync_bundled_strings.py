#!/usr/bin/env python3
"""Regenerates the iOS part of BundledStrings.kt from the iOS SDK's English Localizable.strings.

    python3 tools/sync_bundled_strings.py [path/to/en.lproj/Localizable.strings]

The default path is the iOS checkout next to this repository. Entries above the
"// Android-only keys" marker are rewritten in the iOS file's order; the marker and everything
after it (keys iOS defines in code, Android-only keys) are kept as they are. "App Store" becomes
"Google Play". Run it against the iOS branch you are porting from, then review the diff.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TARGET = ROOT / "vxhub-core/src/main/kotlin/com/vxhub/internal/content/BundledStrings.kt"
DEFAULT_SOURCE = ROOT.parent / "VxHub - iOS/Sources/VxHubCore/Resources/en.lproj/Localizable.strings"
MARKER = "        // Android-only keys"
MAP_OPEN = "    val values: Map<String, String> = mapOf(\n"
ENTRY = re.compile(r'"((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;')
ESCAPE = re.compile(r"\\([uU][0-9a-fA-F]{4}|.)")
SIMPLE_ESCAPES = {"n": "\n", "t": "\t", "r": "\r"}
ANDROID_WORDING = {"App Store": "Google Play"}


def unescape(value: str) -> str:
    def replace(match: re.Match) -> str:
        sequence = match.group(1)
        if sequence[0] in "uU":
            return chr(int(sequence[1:], 16))
        return SIMPLE_ESCAPES.get(sequence, sequence)

    return ESCAPE.sub(replace, value)


def read_strings(path: Path) -> dict[str, str]:
    text = re.sub(r"/\*.*?\*/", "", path.read_text(encoding="utf-8"), flags=re.S)
    text = re.sub(r"(?m)^\s*//.*$", "", text)
    entries: dict[str, str] = {}
    for key, value in ENTRY.findall(text):
        value = unescape(value)
        for ios, android in ANDROID_WORDING.items():
            value = value.replace(ios, android)
        entries[key] = value  # a repeated key keeps its first position and its last value, as on iOS
    return entries


def kotlin_literal(value: str) -> str:
    escaped = value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
    return '"' + escaped.replace("\n", "\\n").replace("\t", "\\t") + '"'


def main() -> int:
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_SOURCE
    if not source.is_file():
        print(f"Localizable.strings not found: {source}", file=sys.stderr)
        return 1
    kotlin = TARGET.read_text(encoding="utf-8")
    head, rest = kotlin.split(MAP_OPEN, 1)
    tail = rest[rest.index(MARKER):]
    tail_keys = set(re.findall(r'(?m)^\s*"([^"]+)" to ', tail))

    entries = read_strings(source)
    duplicated = sorted(tail_keys & entries.keys())
    if duplicated:
        print("iOS now ships these keys too; remove them below the marker:", ", ".join(duplicated))
    lines = [f"        {kotlin_literal(key)} to {kotlin_literal(value)},\n" for key, value in entries.items()]
    TARGET.write_text(head + MAP_OPEN + "".join(lines) + tail, encoding="utf-8")
    print(f"{len(entries)} iOS keys written to {TARGET.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
