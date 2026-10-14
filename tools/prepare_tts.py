"""Prepare bundled offline voices. Development tool; never runs in the Android app.

Usage: python tools/prepare_tts.py [--offline]
Downloads are pinned by SHA-256. Keep .tts-cache to build without a network.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / ".tts-cache"
FILES = json.loads((Path(__file__).with_name("tts-downloads.json")).read_text())
MODEL_FILES = (
    "duration_predictor.int8.onnx", "text_encoder.int8.onnx",
    "vector_estimator.int8.onnx", "vocoder.int8.onnx", "tts.json",
    "unicode_indexer.bin", "voice.bin", "LICENSE",
)


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    CACHE.mkdir(exist_ok=True)
    for entry in FILES:
        target = CACHE / entry["file"]
        if not target.exists():
            if args.offline:
                raise SystemExit(f"Missing {target}; run once without --offline.")
            temporary = target.with_suffix(target.suffix + ".part")
            print(f"Downloading {entry['file']}", flush=True)
            with urllib.request.urlopen(entry["url"], timeout=120) as response, temporary.open("wb") as out:
                shutil.copyfileobj(response, out)
            if digest(temporary) != entry["sha256"]:
                temporary.unlink()
                raise SystemExit(f"SHA-256 mismatch: {target.name}")
            temporary.replace(target)
        if target.stat().st_size != entry["bytes"] or digest(target) != entry["sha256"]:
            raise SystemExit(f"Invalid cached file: {target}. Remove it and retry.")

    destination = CACHE / "assets" / "tts" / "supertonic-3"
    destination.mkdir(parents=True, exist_ok=True)
    marker = destination / ".ready"
    marker.unlink(missing_ok=True)
    # Extract only known regular files to fixed paths; never trust archive paths/symlinks.
    prefix = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11/"
    with tarfile.open(CACHE / "supertonic-3.tar.bz2", "r:bz2") as archive:
        for name in MODEL_FILES:
            member = archive.getmember(prefix + name)
            if not member.isfile() or member.size > 200 * 1024 * 1024:
                raise SystemExit(f"Unexpected archive entry: {name}")
            temporary = destination / (name + ".part")
            with archive.extractfile(member) as source, temporary.open("wb") as out:
                shutil.copyfileobj(source, out)
            temporary.replace(destination / name)
    marker.write_text(FILES[1]["sha256"], encoding="ascii")
    print(f"Offline TTS assets ready: {destination}")


if __name__ == "__main__":
    main()
