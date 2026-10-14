#!/usr/bin/env python3
"""Generate the Spark-Core GameTest platform structure template.

Writes src/main/resources/data/spark_core/structure/empty_platform.nbt -- a flat
5x3x5 stone-brick floor. GameTest reads the template from resources before any
@BeforeBatch runs, so it must be committed rather than built at test time.

Usage: python tools/generate_gametest_structure.py [output.nbt]
"""
import gzip
import struct
import sys
from pathlib import Path

DATA_VERSION = 3955  # Minecraft 1.21.1
SIZE = (5, 3, 5)
FLOOR_BLOCK = "minecraft:stone_bricks"


def tag_string(value: str) -> bytes:
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def tag_int(value: int) -> bytes:
    return struct.pack(">i", value)


def tag_int_list(values) -> bytes:
    return struct.pack(">b", 3) + struct.pack(">i", len(values)) + b"".join(tag_int(v) for v in values)


def tag_compound_list(payloads) -> bytes:
    """A TAG_List whose element type is TAG_Compound; each payload already ends with TAG_End."""
    return struct.pack(">b", 10) + struct.pack(">i", len(payloads)) + b"".join(payloads)


def compound(fields: bytes) -> bytes:
    return fields + b"\x00"  # TAG_End


def named(tag_type: int, name: str, payload: bytes) -> bytes:
    return struct.pack(">b", tag_type) + tag_string(name) + payload


def build() -> bytes:
    body = b""
    body += named(3, "DataVersion", tag_int(DATA_VERSION))
    body += named(9, "size", tag_int_list(SIZE))

    palette_entry = compound(named(8, "Name", tag_string(FLOOR_BLOCK)))
    body += named(9, "palette", tag_compound_list([palette_entry]))

    blocks = []
    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            entry = compound(
                named(9, "pos", tag_int_list([x, 0, z]))
                + named(3, "state", tag_int(0))
            )
            blocks.append(entry)
    body += named(9, "blocks", tag_compound_list(blocks))
    body += named(9, "entities", tag_compound_list([]))

    return struct.pack(">b", 10) + tag_string("") + compound(body)


def main() -> None:
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else (
        Path(__file__).resolve().parent.parent
        / "src/main/resources/data/spark_core/structure/empty_platform.nbt"
    )
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(gzip.compress(build(), mtime=0))
    print(f"wrote {out} ({out.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
