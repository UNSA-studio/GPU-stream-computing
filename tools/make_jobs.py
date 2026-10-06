#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
make_jobs.py — 根据 region_report.json 生成 gpusc 任务队列（只针对"不完整区域"）
用法: python3 make_jobs.py [state文件路径]
"""
import json
import os
import sys

REPORT = "/root/region_report.json"
STATE = sys.argv[1] if len(sys.argv) > 1 else "/root/mcserver/world/gpusc_state.json"

def main():
    with open(REPORT) as f:
        rep = json.load(f)
    incomplete = rep["incomplete"]

    jobs = {}
    for it in incomplete:
        rx, rz = it["rx"], it["rz"]
        jid = "r.%d.%d" % (rx, rz)
        jobs[jid] = {
            "id": jid,
            "rx": rx,
            "rz": rz,
            "state": "todo",
            "worker": "",
            "claimedAt": 0,
            "finishedAt": 0,
        }

    state = {"jobs": jobs, "workers": {}}

    if os.path.exists(STATE):
        bak = STATE + ".bak.%d" % int(os.path.getmtime(STATE))
        os.system("cp -f '%s' '%s'" % (STATE, bak))
        print("backup ->", bak)

    with open(STATE, "w") as f:
        json.dump(state, f, indent=1)

    total_missing = sum(it["missing"] for it in incomplete)
    print("jobs written: %d regions, missing chunks: %d" % (len(jobs), total_missing))
    print("state ->", STATE)

if __name__ == "__main__":
    main()