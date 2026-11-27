#!/usr/bin/env python3
"""Generate a small recognizable structure for the builtin Spark pack."""
import gzip
import struct
from pathlib import Path

DATA_VERSION = 3955  # Minecraft 1.21.1
OUTPUT = (
    Path(__file__).resolve().parent.parent
    / "src/main/resources/spark_modules/spark_core.default/spark_core/structure/worldgen_probe.nbt"
)


def tag_string(value: str) -> bytes:
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def tag_int(value: int) -> bytes:
    return struct.pack(">i", value)


def tag_int_list(values) -> bytes:
    return struct.pack(">b", 3) + struct.pack(">i", len(values)) + b"".join(tag_int(v) for v in values)


def tag_compound_list(payloads) -> bytes:
    return struct.pack(">b", 10) + struct.pack(">i", len(payloads)) + b"".join(payloads)


def compound(fields: bytes) -> bytes:
    return fields + b"\x00"


def named(tag_type: int, name: str, payload: bytes) -> bytes:
    return struct.pack(">b", tag_type) + tag_string(name) + payload


def build() -> bytes:
    palette = [
        compound(named(8, "Name", tag_string("minecraft:iron_block"))),
        compound(named(8, "Name", tag_string("minecraft:beacon"))),
    ]
    blocks = []
    for x in range(3):
        for z in range(3):
            blocks.append(
                compound(
                    named(9, "pos", tag_int_list([x, 0, z]))
                    + named(3, "state", tag_int(0))
                )
            )
    blocks.append(
        compound(
            named(9, "pos", tag_int_list([1, 1, 1]))
            + named(3, "state", tag_int(1))
        )
    )

    body = b""
    body += named(3, "DataVersion", tag_int(DATA_VERSION))
    body += named(9, "size", tag_int_list([3, 2, 3]))
    body += named(9, "palette", tag_compound_list(palette))
    body += named(9, "blocks", tag_compound_list(blocks))
    body += named(9, "entities", tag_compound_list([]))
    return struct.pack(">b", 10) + tag_string("") + compound(body)


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_bytes(gzip.compress(build(), mtime=0))
    print(f"wrote {OUTPUT} ({OUTPUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
