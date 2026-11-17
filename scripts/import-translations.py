#!/usr/bin/env python3
"""Import the original client's ARB text without a Flutter runtime."""

import argparse
import json
from pathlib import Path


def literal(value):
    return json.dumps(value, ensure_ascii=False).replace("$", "\\$")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/Translations.kt")
    args = parser.parse_args()
    lines = [
        "package com.quanquan.quantoot.ui", "",
        "class Strings(val locale: String, val systemLanguage: String) {",
        '    private val selected = if (locale == "system") systemLanguage else locale',
        '    private val catalog = when {',
        '        selected.startsWith("zh", ignoreCase = true) && (selected.contains("TW", true) || selected.contains("HK", true) || selected.contains("Hant", true)) -> traditional',
        '        selected.startsWith("zh", ignoreCase = true) -> chinese',
        '        else -> english',
        '    }',
        '    fun text(key: String, vararg args: Pair<String, String>): String {',
        '        var value = catalog[key] ?: english[key] ?: key',
        '        args.forEach { (name, replacement) -> value = value.replace("{$name}", replacement) }',
        '        return value',
        '    }',
        '}', "",
    ]
    for locale, name in [("en", "english"), ("zh", "chinese"), ("zh_TW", "traditional")]:
        raw = json.loads((args.source / f"app_{locale}.arb").read_text())
        values = [(key, value) for key, value in raw.items() if not key.startswith("@") and isinstance(value, str)]
        parts = [values[index:index + 90] for index in range(0, len(values), 90)]
        lines += [f"private val {name} = buildMap<String, String> {{"]
        lines += [f"    putAll({name}{index}())" for index in range(len(parts))]
        lines += ["}", ""]
        for index, part in enumerate(parts):
            lines += [f"private fun {name}{index}(): Map<String, String> = mapOf("]
            lines += [f"    {literal(key)} to {literal(value)}," for key, value in part]
            lines += [")", ""]
        print(f"{locale}: {len(values)} strings")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(lines))


if __name__ == "__main__":
    main()
