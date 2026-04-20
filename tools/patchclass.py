#!/usr/bin/env python3
"""
Patch a compiled Java .class file:
replace references to java/lang/Thread.stop()V
with references to java/lang/Thread.interrupt()V.

Usage:
    python patch_thread_stop.py input.class [output.class]

If output.class is omitted, writes:
    input.patched.class
"""

from __future__ import annotations

import struct
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional


# Constant pool tags
CONSTANT_Utf8 = 1
CONSTANT_Integer = 3
CONSTANT_Float = 4
CONSTANT_Long = 5
CONSTANT_Double = 6
CONSTANT_Class = 7
CONSTANT_String = 8
CONSTANT_Fieldref = 9
CONSTANT_Methodref = 10
CONSTANT_InterfaceMethodref = 11
CONSTANT_NameAndType = 12
CONSTANT_MethodHandle = 15
CONSTANT_MethodType = 16
CONSTANT_Dynamic = 17
CONSTANT_InvokeDynamic = 18
CONSTANT_Module = 19
CONSTANT_Package = 20


@dataclass
class CpEntry:
    tag: int
    data: bytes


def u1(b: bytes, off: int) -> tuple[int, int]:
    return b[off], off + 1


def u2(b: bytes, off: int) -> tuple[int, int]:
    return struct.unpack_from(">H", b, off)[0], off + 2


def u4(b: bytes, off: int) -> tuple[int, int]:
    return struct.unpack_from(">I", b, off)[0], off + 4


def parse_constant_pool(data: bytes, cp_count: int, off: int) -> tuple[List[Optional[CpEntry]], int]:
    """
    Returns:
        cp_entries: 1-based list stored as 0-based array of length cp_count-1.
                    Long/Double second slot is represented as None.
        off: byte offset after constant pool.
    """
    cp_entries: List[Optional[CpEntry]] = []
    i = 1
    while i < cp_count:
        tag, off = u1(data, off)

        if tag == CONSTANT_Utf8:
            length, off = u2(data, off)
            payload = data[off:off + length]
            off += length
            cp_entries.append(CpEntry(tag, struct.pack(">H", length) + payload))

        elif tag in (CONSTANT_Integer, CONSTANT_Float):
            payload = data[off:off + 4]
            off += 4
            cp_entries.append(CpEntry(tag, payload))

        elif tag in (CONSTANT_Long, CONSTANT_Double):
            payload = data[off:off + 8]
            off += 8
            cp_entries.append(CpEntry(tag, payload))
            cp_entries.append(None)  # extra slot
            i += 1

        elif tag in (
            CONSTANT_Class,
            CONSTANT_String,
            CONSTANT_MethodType,
            CONSTANT_Module,
            CONSTANT_Package,
        ):
            payload = data[off:off + 2]
            off += 2
            cp_entries.append(CpEntry(tag, payload))

        elif tag in (
            CONSTANT_Fieldref,
            CONSTANT_Methodref,
            CONSTANT_InterfaceMethodref,
            CONSTANT_NameAndType,
            CONSTANT_Dynamic,
            CONSTANT_InvokeDynamic,
        ):
            payload = data[off:off + 4]
            off += 4
            cp_entries.append(CpEntry(tag, payload))

        elif tag == CONSTANT_MethodHandle:
            payload = data[off:off + 3]
            off += 3
            cp_entries.append(CpEntry(tag, payload))

        else:
            raise ValueError(f"Unsupported or invalid constant pool tag {tag} at entry {i}")

        i += 1

    return cp_entries, off


def cp_utf8(cp: List[Optional[CpEntry]], idx: int) -> str:
    entry = cp[idx - 1]
    if entry is None or entry.tag != CONSTANT_Utf8:
        raise ValueError(f"CP entry {idx} is not Utf8")
    length = struct.unpack(">H", entry.data[:2])[0]
    raw = entry.data[2:2 + length]
    return raw.decode("utf-8")


def cp_class_name(cp: List[Optional[CpEntry]], idx: int) -> str:
    entry = cp[idx - 1]
    if entry is None or entry.tag != CONSTANT_Class:
        raise ValueError(f"CP entry {idx} is not Class")
    name_index = struct.unpack(">H", entry.data)[0]
    return cp_utf8(cp, name_index)


def cp_name_and_type(cp: List[Optional[CpEntry]], idx: int) -> tuple[str, str]:
    entry = cp[idx - 1]
    if entry is None or entry.tag != CONSTANT_NameAndType:
        raise ValueError(f"CP entry {idx} is not NameAndType")
    name_index, desc_index = struct.unpack(">HH", entry.data)
    return cp_utf8(cp, name_index), cp_utf8(cp, desc_index)


def serialize_constant_pool(cp: List[Optional[CpEntry]]) -> bytes:
    out = bytearray()
    for entry in cp:
        if entry is None:
            continue
        out.append(entry.tag)
        out.extend(entry.data)
    return bytes(out)


def append_utf8(cp: List[Optional[CpEntry]], text: str) -> int:
    encoded = text.encode("utf-8")
    if len(encoded) > 65535:
        raise ValueError("Utf8 constant too long")
    cp.append(CpEntry(CONSTANT_Utf8, struct.pack(">H", len(encoded)) + encoded))
    return len(cp)


def append_name_and_type(cp: List[Optional[CpEntry]], name_index: int, desc_index: int) -> int:
    cp.append(CpEntry(CONSTANT_NameAndType, struct.pack(">HH", name_index, desc_index)))
    return len(cp)


def find_utf8(cp: List[Optional[CpEntry]], text: str) -> Optional[int]:
    for i, entry in enumerate(cp, start=1):
        if entry is not None and entry.tag == CONSTANT_Utf8:
            if cp_utf8(cp, i) == text:
                return i
    return None


def find_name_and_type(cp: List[Optional[CpEntry]], name: str, desc: str) -> Optional[int]:
    for i, entry in enumerate(cp, start=1):
        if entry is not None and entry.tag == CONSTANT_NameAndType:
            n, d = cp_name_and_type(cp, i)
            if n == name and d == desc:
                return i
    return None


def patch_thread_stop_to_interrupt(data: bytes) -> tuple[bytes, int]:
    if data[:4] != b"\xCA\xFE\xBA\xBE":
        raise ValueError("Not a valid Java .class file")

    minor = struct.unpack_from(">H", data, 4)[0]
    major = struct.unpack_from(">H", data, 6)[0]
    cp_count = struct.unpack_from(">H", data, 8)[0]

    cp, off_after_cp = parse_constant_pool(data, cp_count, 10)

    # Find or create Utf8 for "interrupt" and NameAndType interrupt:()V
    interrupt_utf8_idx = find_utf8(cp, "interrupt")
    if interrupt_utf8_idx is None:
        interrupt_utf8_idx = append_utf8(cp, "interrupt")

    void_desc_utf8_idx = find_utf8(cp, "()V")
    if void_desc_utf8_idx is None:
        void_desc_utf8_idx = append_utf8(cp, "()V")

    interrupt_nat_idx = find_name_and_type(cp, "interrupt", "()V")
    if interrupt_nat_idx is None:
        interrupt_nat_idx = append_name_and_type(cp, interrupt_utf8_idx, void_desc_utf8_idx)

    patched_count = 0

    for i, entry in enumerate(cp, start=1):
        if entry is None or entry.tag != CONSTANT_Methodref:
            continue

        class_index, nat_index = struct.unpack(">HH", entry.data)
        try:
            class_name = cp_class_name(cp, class_index)
            method_name, method_desc = cp_name_and_type(cp, nat_index)
        except Exception:
            continue

        if class_name == "java/lang/Thread" and method_name == "stop" and method_desc == "()V":
            cp[i - 1] = CpEntry(CONSTANT_Methodref, struct.pack(">HH", class_index, interrupt_nat_idx))
            patched_count += 1

    new_cp_count = len(cp) + 1
    new_bytes = bytearray()
    new_bytes.extend(b"\xCA\xFE\xBA\xBE")
    new_bytes.extend(struct.pack(">H", minor))
    new_bytes.extend(struct.pack(">H", major))
    new_bytes.extend(struct.pack(">H", new_cp_count))
    new_bytes.extend(serialize_constant_pool(cp))
    new_bytes.extend(data[off_after_cp:])

    return bytes(new_bytes), patched_count


def main() -> int:
    if len(sys.argv) not in (2, 3):
        print("Usage: python patch_thread_stop.py input.class [output.class]", file=sys.stderr)
        return 2

    in_path = Path(sys.argv[1])
    if len(sys.argv) == 3:
        out_path = Path(sys.argv[2])
    else:
        out_path = in_path.with_suffix(".patched.class")

    data = in_path.read_bytes()
    patched_data, patched_count = patch_thread_stop_to_interrupt(data)

    out_path.write_bytes(patched_data)

    print(f"Read:    {in_path}")
    print(f"Wrote:   {out_path}")
    print(f"Patched method references: {patched_count}")

    if patched_count == 0:
        print("Warning: no java/lang/Thread.stop()V method references were found.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

