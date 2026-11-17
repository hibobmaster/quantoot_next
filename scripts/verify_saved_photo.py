#!/usr/bin/env python3
import argparse
import hashlib
import json
import sqlite3
import urllib.request
from pathlib import Path


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def jpeg_without_exif(data):
    if not data.startswith(b"\xff\xd8"):
        return None
    parts = [data[:2]]
    offset = 2
    while offset + 4 <= len(data):
        if data[offset] != 0xFF:
            raise ValueError("Invalid JPEG segment")
        marker = data[offset + 1]
        if marker in (0xDA, 0xD9):
            return b"".join(parts) + data[offset:]
        length = int.from_bytes(data[offset + 2:offset + 4], "big")
        end = offset + length + 2
        if length < 2 or end > len(data):
            raise ValueError("Invalid JPEG segment length")
        segment = data[offset:end]
        if not (marker == 0xE1 and segment[4:10] == b"Exif\x00\x00"):
            parts.append(segment)
        offset = end
    raise ValueError("JPEG has no image scan")


def main():
    parser = argparse.ArgumentParser(description="Verify only new simulator Photos resources against a selected original.")
    parser.add_argument("--simulator", required=True)
    parser.add_argument("--before", type=Path, required=True)
    parser.add_argument("--original", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    before = json.loads(args.before.read_text())
    original = json.loads(args.original.read_text())
    with urllib.request.urlopen(original["url"], timeout=30) as response:
        source = response.read(30 * 1024 * 1024 + 1)
    if len(source) > 30 * 1024 * 1024 or sha256(source) != original["sha256"]:
        raise ValueError("The selected original changed or exceeds the save limit")
    media = Path.home() / "Library/Developer/CoreSimulator/Devices" / args.simulator / "data/Media"
    database = media / "PhotoData/Photos.sqlite"
    connection = sqlite3.connect(f"file:{database}?mode=ro", uri=True)
    try:
        count = connection.execute("SELECT COUNT(*) FROM ZASSET").fetchone()[0]
        rows = connection.execute(
            "SELECT Z_PK,ZDIRECTORY,ZFILENAME,ZWIDTH,ZHEIGHT,ZUUID FROM ZASSET WHERE Z_PK>? ORDER BY Z_PK",
            (before["maximumAssetPrimaryKey"],),
        ).fetchall()
    finally:
        connection.close()
    source_image = jpeg_without_exif(source)
    assets = []
    for primary_key, directory, filename, width, height, uuid in rows:
        path = media / directory / filename
        resource = path.read_bytes()
        exact = resource == source
        saved_image = jpeg_without_exif(resource) if source_image is not None else None
        image_match = exact or (source_image is not None and saved_image == source_image)
        dimensions = original.get("originalMeta") or {}
        dimensions_match = (width, height) == (dimensions.get("width"), dimensions.get("height"))
        assets.append({
            "primaryKey": primary_key,
            "relativePath": str(path.relative_to(media)),
            "uuid": uuid,
            "width": width,
            "height": height,
            "sizeBytes": len(resource),
            "sha256": sha256(resource),
            "wholeFileMatches": exact,
            "originalImageDataMatches": image_match,
            "originalDimensionsMatch": dimensions_match,
            "comparison": "exact-file" if exact else "all-JPEG-bytes-except-Exif-APP1",
            "jpegDataSha256": sha256(saved_image) if saved_image is not None else None,
        })
    verified = count > before["count"] and any(
        item["originalImageDataMatches"] and item["originalDimensionsMatch"] for item in assets
    )
    result = {
        "beforeCount": before["count"],
        "afterCount": count,
        "selectedAttachmentID": original["attachmentID"],
        "selectedOriginalSha256": original["sha256"],
        "selectedJpegDataSha256": sha256(source_image) if source_image is not None else None,
        "newAssets": assets,
        "verified": verified,
        "metadataNormalization": "Photos may rewrite EXIF; whole-file identity is reported separately from original image-data identity.",
    }
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))
    if not verified:
        raise SystemExit("No new Photos resource matches the selected original image data and dimensions")


if __name__ == "__main__":
    main()
