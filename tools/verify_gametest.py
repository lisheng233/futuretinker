#!/usr/bin/env python3
"""Future Tinker 的判定：跑 GameTestServer，并且**要求日志里出现测试证据**。

为什么不直接看 gradle 的退出码：实测过——模组加载期崩溃时 `runGameTestServer`
一样会 `BUILD SUCCESSFUL`、退出码 0。那种情况下「退出码 = 失败的必需测试数」
这条契约根本不成立（游戏没走到测试），只看退出码就会把一次彻底失败读成通过。
所以这里的判据是**日志里的证据 + 退出码交叉验证**：

  * 必须出现 `Running test batch '...' (N tests)...`（否则 = 没跑到测试）；
  * 必须出现 `All N required tests passed :)` 或 `N required tests failed :(`；
  * 两者与 gradle 退出码必须自洽。

退出码（本脚本自己的，不是 gradle 的）：
  0  通过：有测试证据、全部必需测试通过、gradle 退出码 0
  1  测试失败（或 gradle 退出码非 0 且日志确认测试失败）
  2  没有跑到测试：编译失败、模组加载崩溃、或测试根本没注册（"No test batches"）
  3  证据自相矛盾（日志与退出码对不上）
  --fault 模式：0 = 注入的故障被抓到，4 = 故障没被抓到（判定不会说「不」），1/2/3 同上

用法：
  python tools/verify_gametest.py              # 正常跑
  python tools/verify_gametest.py --fault      # 注入故障，必须失败
  python tools/verify_gametest.py --tail 60    # 失败时多打几行日志
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import time
from pathlib import Path

RE_ENABLED_NS = re.compile(r"Enabled Gametest Namespaces: \[(.*?)\]")
RE_RUNNING = re.compile(r"Running test batch '([^']*)' \((\d+) tests?\)")
RE_NOW_RUNNING = re.compile(r"(\d+) tests are now running!")
RE_PASSED = re.compile(r"All (\d+) required tests passed :\)")
RE_FAILED = re.compile(r"(\d+) required tests failed :\(")
RE_FAILED_NAMES = re.compile(r"\]:\s+-\s+(\w+)\s*$", re.MULTILINE)
RE_NO_BATCHES = re.compile(r"No test batches were given!")
RE_CRASH = re.compile(r"Game test server crashed")
RE_SWITCHES = re.compile(r"Future Tinker 模块开关（注册表填充前.*$", re.MULTILINE)
RE_GATE = re.compile(r"Future Tinker 模块闸门（注册阶段）.*$", re.MULTILINE)

EXIT_OK = 0
EXIT_TESTS_FAILED = 1
EXIT_NOT_REACHED = 2
EXIT_INCONSISTENT = 3
EXIT_FAULT_NOT_CAUGHT = 4


def decode(raw: bytes) -> str:
    """游戏进程在不同终端下输出 UTF-8 或 GBK；两种都试，最后兜底。"""
    for encoding in ("utf-8", "gbk"):
        try:
            return raw.decode(encoding)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="replace")


def wrapper(project: Path) -> Path:
    name = "gradlew.bat" if os.name == "nt" else "gradlew"
    path = project / name
    if not path.is_file():
        sys.exit(f"[verify] 找不到构建脚本: {path}")
    return path


def run_gametest(project: Path, log_path: Path, fault: bool, timeout: int) -> int:
    cmd = [str(wrapper(project)), "runGameTestServer", "--console=plain", "--no-daemon"]
    if fault:
        cmd.append("-PftFault")
    log_path.parent.mkdir(parents=True, exist_ok=True)
    started = time.time()
    print(f"[verify] {' '.join(cmd)}")
    print(f"[verify] 日志 -> {log_path}")
    # 直接把子进程输出写进文件（不开管道）：既不会被管道限制卡住，也不会丢证据。
    with open(log_path, "wb") as log:
        try:
            completed = subprocess.run(cmd, cwd=str(project), stdout=log,
                                       stderr=subprocess.STDOUT, timeout=timeout)
            code = completed.returncode
        except subprocess.TimeoutExpired:
            print(f"[verify] 超时 {timeout}s，进程已终止")
            return EXIT_NOT_REACHED
    print(f"[verify] gradle 退出码 {code}，用时 {time.time() - started:.0f}s")
    return code


def analyse(text: str) -> dict:
    running = RE_RUNNING.findall(text)
    passed = [int(n) for n in RE_PASSED.findall(text)]
    failed = [int(n) for n in RE_FAILED.findall(text)]
    return {
        "namespaces": RE_ENABLED_NS.findall(text),
        "batches": running,
        "tests_started": sum(int(n) for _, n in running),
        "now_running": [int(n) for n in RE_NOW_RUNNING.findall(text)],
        "passed": passed[-1] if passed else None,
        "failed": failed[-1] if failed else None,
        "no_batches": bool(RE_NO_BATCHES.search(text)),
        "crashed": bool(RE_CRASH.search(text)),
        "switches": RE_SWITCHES.findall(text),
        "gate": RE_GATE.findall(text),
        "failed_names": RE_FAILED_NAMES.findall(text),
    }


def report(info: dict) -> None:
    print("[verify] ---- 日志证据 ----")
    if info["namespaces"]:
        print(f"[verify]   允许的测试命名空间: {info['namespaces']}")
    if info["batches"]:
        for name, count in info["batches"]:
            print(f"[verify]   批次 {name}: {count} 个测试")
    if info["passed"] is not None:
        print(f"[verify]   All {info['passed']} required tests passed :)")
    if info["failed"] is not None:
        print(f"[verify]   {info['failed']} required tests failed :(")
        for name in info["failed_names"]:
            print(f"[verify]     - {name}")
    if info["no_batches"]:
        print("[verify]   'No test batches were given!'")
    if info["crashed"]:
        print("[verify]   'Game test server crashed'")
    for line in info["switches"]:
        print(f"[verify]   {line.strip()}")
    for line in info["gate"]:
        print(f"[verify]   {line.strip()}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Future Tinker GameTest 判定（要求日志证据）")
    root = Path(__file__).resolve().parent.parent
    parser.add_argument("--project", default=str(root), help="模组工程目录（默认本脚本的上一级）")
    parser.add_argument("--fault", action="store_true", help="注入故障：预期判定失败")
    parser.add_argument("--timeout", type=int, default=1800, help="子进程超时（秒）")
    parser.add_argument("--tail", type=int, default=25, help="失败时额外打印的日志行数")
    args = parser.parse_args()

    project = Path(args.project).resolve()
    log_path = project / "build" / ("verify-gametest-fault.log" if args.fault else "verify-gametest.log")

    code = run_gametest(project, log_path, args.fault, args.timeout)
    text = decode(log_path.read_bytes()) if log_path.is_file() else ""
    info = analyse(text)
    report(info)

    reached = bool(info["batches"])
    if not reached:
        print("[verify] 判定: 没跑到测试（没有 'Running test batch'）——不能当作通过")
        if info["no_batches"]:
            print("[verify] 原因: 没有任何测试批次被注册（命名空间过滤？@GameTest 没被扫描到？）")
        if code != 0:
            print("[verify] 注意: gradle 退出码非 0，但真正的原因在日志里，不是测试计数")
        tail = text.splitlines()[-args.tail:]
        if tail:
            print("[verify] ---- 日志末尾 ----")
            for line in tail:
                print(f"[verify]   {line.rstrip()}")
        return EXIT_NOT_REACHED

    log_failed = info["failed"] is not None and (info["failed"] or 0) > 0
    log_passed = info["passed"] is not None

    if code != 0 and log_passed and not log_failed:
        print("[verify] 判定: 证据自相矛盾——日志说全部通过，gradle 退出码却非 0")
        return EXIT_INCONSISTENT
    if code == 0 and log_failed:
        print("[verify] 判定: 证据自相矛盾——日志说测试失败，gradle 退出码却是 0")
        return EXIT_INCONSISTENT

    if args.fault:
        if log_failed:
            print("[verify] 判定: 注入的故障被抓住了（这就是要的结果）")
            return EXIT_OK
        print("[verify] 判定: 故障没被抓到——这套判定已经不会说「不」了")
        return EXIT_FAULT_NOT_CAUGHT

    if log_failed:
        print("[verify] 判定: 失败")
        return EXIT_TESTS_FAILED

    print("[verify] 判定: 通过")
    return EXIT_OK


if __name__ == "__main__":
    sys.exit(main())
