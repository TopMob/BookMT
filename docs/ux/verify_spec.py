"""Validate this design package; standard library only, no app/runtime dependency."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
HEX = re.compile(r"^#[0-9A-F]{6}$")


def rgb(value):
    return tuple(int(value[index:index + 2], 16) / 255 for index in (1, 3, 5))


def luminance(color):
    linear = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in color]
    return sum(c * w for c, w in zip(linear, (0.2126, 0.7152, 0.0722)))


def contrast(a, b):
    hi, lo = sorted((luminance(a), luminance(b)), reverse=True)
    return (hi + 0.05) / (lo + 0.05)


def composite(foreground, background, alpha):
    return tuple(f * alpha + b * (1 - alpha) for f, b in zip(foreground, background))


def main():
    errors = []
    data = json.loads((ROOT / "theme-tokens.json").read_text(encoding="utf-8"))
    palettes = data["themes"]
    expected_keys = set(palettes["day"])
    checks = 0
    for name, values in palettes.items():
        assert set(values) == expected_keys, f"Inconsistent theme keys: {name}"
        assert 0 <= values["scrimAlpha"] <= 1
        colors = {}
        for key, value in values.items():
            if key == "scrimAlpha":
                continue
            assert HEX.fullmatch(value), f"Invalid color: {name}.{key}"
            colors[key] = rgb(value)
        results = []

        def check(label, foreground, background, threshold=4.5):
            nonlocal checks
            ratio = contrast(foreground, background)
            checks += 1
            results.append((ratio, label))
            if ratio < threshold:
                errors.append(f"{name} {label}: {ratio:.2f} < {threshold}")

        for surface in ("background", "surface", "surfaceRaised", "surfaceInset"):
            for text in ("text", "secondaryText", "accent", "error", "warning", "success"):
                check(f"{text}/{surface}", colors[text], colors[surface])
            check(f"border/{surface}", colors["border"], colors[surface], 3)
            check(f"pressed text/{surface}", colors["text"],
                  composite(colors["text"], colors[surface], 0.08))
            check(f"pressed secondary/{surface}", colors["secondaryText"],
                  composite(colors["text"], colors[surface], 0.08))
            check(f"pressed accent/{surface}", colors["accent"],
                  composite(colors["text"], colors[surface], 0.08))
        for foreground, background in (
            ("onAccent", "accent"), ("onAccentContainer", "accentContainer"),
            ("onError", "error"), ("error", "errorContainer"),
            ("warning", "warningContainer"), ("success", "successContainer"),
            ("inverseText", "inverseSurface"), ("inverseAccent", "inverseSurface"),
        ):
            check(f"{foreground}/{background}", colors[foreground], colors[background])
        check("pressed primary", colors["onAccent"],
              composite(colors["onAccent"], colors["accent"], 0.1))
        for background in ("readerBackground", "selection", "tts", "search", "searchCurrent",
                           "markYellow", "markGreen", "markBlue", "markPink"):
            check(f"readerText/{background}", colors["readerText"], colors[background])
        ratio, label = min(results)
        print(f"{name}: {len(results)} contrast checks; minimum {ratio:.2f}:1 ({label})")

    for document in ROOT.glob("*.md"):
        source = document.read_text(encoding="utf-8")
        assert source.count("```") % 2 == 0, f"Unclosed code block: {document.name}"
        for target in re.findall(r"\]\(([^)]+)\)", source):
            if "://" in target or target.startswith("#"):
                continue
            path = target.split("#", 1)[0]
            if not (document.parent / path).is_file():
                errors.append(f"Broken link: {document.name} -> {target}")
    runtime = ROOT.parent.parent / "app/src/main/java/com/TopMob/bookmt/presentation/theme/ComfortPalette.kt"
    if runtime.exists():
        kotlin = runtime.read_text(encoding="utf-8")
        for name, tokens in palettes.items():
            match = re.search(r"val " + name.capitalize() + r" = ComfortPalette\((.*?)\n    \)", kotlin, re.S)
            assert match, f"Missing runtime palette {name}"
            for key, value in tokens.items():
                expected = str(value) + "f" if key == "scrimAlpha" else "Color(0xFF" + value[1:] + ")"
                if not re.search(r"\b" + key + r" = " + re.escape(expected) + r",", match.group(1)):
                    errors.append(f"Runtime token differs from design: {name}.{key}")
        print("Runtime Kotlin palettes match the design tokens.")
    if errors:
        raise SystemExit("\n".join(errors))
    print(f"PASS: {checks} contrast checks, theme schema, local Markdown links and code fences.")
    print("Rendering, focus, font scaling, screenshots and physical-device comfort still require implementation QA.")


if __name__ == "__main__":
    main()
