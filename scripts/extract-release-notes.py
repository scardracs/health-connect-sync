#!/usr/bin/env python3
"""Write the English section of RELEASE_NOTES.md for one version."""

import re
import sys
from pathlib import Path


def english_notes(markdown: str, version: str) -> str:
    version = version.removeprefix("v")
    section = re.search(
        rf"^# Salute Sync v{re.escape(version)}\s*$([\s\S]*?)(?=^# |\Z)",
        markdown,
        re.M,
    )
    body = section.group(1) if section else markdown
    english = re.search(r"^## English\s*$([\s\S]*?)(?=^## |\Z)", body, re.M)
    text = (english.group(1) if english else body).strip()
    if not text:
        text = f"Salute Sync {version}."
    return text + "\n"


def main() -> None:
    version, source, dest = sys.argv[1:]
    Path(dest).write_text(english_notes(Path(source).read_text(), version), encoding="utf-8")


if __name__ == "__main__":
    main()
