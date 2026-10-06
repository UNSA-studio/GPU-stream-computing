#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
region_chunks.py — 精确统计每个 .mca 区域文件里已存在的区块数量
用法: python3 region_chunks.py <region目录> [safe_radius_blocks]
输出: 每个区域 (rx,rz) 的已有区块数 / 缺失数，并按"内圈/外圈"分类
"""
import os
import re
import struct
import sys
import json

def chunk_count(path):
    """读前 4096 字节的表头，offset != 0 即该区块存在"""
    try:
        with open(path, "rb") as f:
            head = f.read(4096)
    except OSError:
        return -1
    if len(head) < 4096:
        return -1
    n = 0
    for i in range(1024):
        off = struct.unpack(">I", head[i * 4:i * 4 + 4])[0]
        if off != 0:
            n += 1
    return n

def main():
    region_dir = sys.argv[1] if len(sys.argv) > 1 else "/root/mcserver/world/region"
    safe = int(sys.argv[2]) if len(sys.argv) > 2 else 2000
    rx0, rx1 = -8, 7   # -8..7  ← ±4000 格
    rz0, rz1 = -8, 7

    rows = []
    for rx in range(rx0, rx1 + 1):
        for rz in range(rz0, rz1 + 1):
            p = os.path.join(region_dir, "r.%d.%d.mca" % (rx, rz))
            if not os.path.exists(p):
                rows.append((rx, rz, 0, 1024, "MISSING", os.path.getsize(p) if os.path.exists(p) else 0))
                continue
            n = chunk_count(p)
            missing = 1024 - n
            # 区域覆盖的方块范围中心
            cx = rx * 512 + 256
            cz = rz * 512 + 256
            zone = "inner" if (abs(cx) <= safe and abs(cz) <= safe) else "outer"
            rows.append((rx, rz, n, missing, zone, os.path.getsize(p)))

    total_chunks = 0
    total_missing = 0
    inner_missing = 0
    outer_missing = 0
    full = 0
    incomplete = []
    for rx, rz, n, missing, zone, size in rows:
        total_chunks += n
        total_missing += missing
        if missing == 0:
            full += 1
        else:
            incomplete.append((rx, rz, n, missing, zone, size))
            if zone == "inner":
                inner_missing += missing
            else:
                outer_missing += missing

    print("=== 汇总（±4000 区域 共 %d 个） ===" % len(rows))
    print("完整区域: %d" % full)
    print("不完整区域: %d" % len(incomplete))
    print("已有区块总数: %d / %d" % (total_chunks, len(rows) * 1024))
    print("缺失区块总数: %d  (内圈%d / 外圈%d)" % (total_missing, inner_missing, outer_missing))
    print()
    incomplete.sort(key=lambda r: -r[3])
    print("=== 缺失最多的前 40 个区域 ===")
    print(" rx   rz   已有  缺失   zone   size(MB)")
    for rx, rz, n, missing, zone, size in incomplete[:40]:
        print("%3d  %3d  %5d %5d  %-6s %8.2f" % (rx, rz, n, missing, zone, size / 1048576.0))

    out = {
        "inner_missing": inner_missing,
        "outer_missing": outer_missing,
        "incomplete": [
            {"rx": rx, "rz": rz, "have": n, "missing": missing, "zone": zone}
            for rx, rz, n, missing, zone, size in incomplete
        ],
    }
    with open("/root/region_report.json", "w") as f:
        json.dump(out, f, indent=1)
    print()
    print("report -> /root/region_report.json")

if __name__ == "__main__":
    main()
