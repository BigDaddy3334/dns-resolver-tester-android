#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import stat
import urllib.request
import zipfile
from pathlib import Path
from tempfile import TemporaryDirectory


ROOT = Path(__file__).resolve().parents[1]
JNI_LIBS = ROOT / "app" / "src" / "main" / "jniLibs"
RELEASE_API = "https://api.github.com/repos/nullroute1970/StormDNS/releases/latest"


ASSETS = {
    "arm64-v8a": "StormDNS_Client_Termux_ARM64.zip",
    "armeabi-v7a": "StormDNS_Client_Termux_ARMV7.zip",
}


def download(url: str, path: Path) -> None:
    with urllib.request.urlopen(url, timeout=60) as response:
        path.write_bytes(response.read())


def main() -> int:
    with urllib.request.urlopen(RELEASE_API, timeout=30) as response:
        release = json.load(response)

    by_name = {asset["name"]: asset["browser_download_url"] for asset in release["assets"]}
    print("release:", release["tag_name"])

    with TemporaryDirectory(prefix="stormdns-assets-") as tmp:
        tmpdir = Path(tmp)
        for abi, asset_name in ASSETS.items():
            url = by_name.get(asset_name)
            if not url:
                raise SystemExit(f"missing release asset: {asset_name}")

            zip_path = tmpdir / asset_name
            download(url, zip_path)

            extract_dir = tmpdir / abi
            extract_dir.mkdir()
            with zipfile.ZipFile(zip_path) as archive:
                archive.extractall(extract_dir)

            candidates = [
                path for path in extract_dir.iterdir()
                if path.is_file() and path.name.startswith("StormDNS_Client_Termux_")
            ]
            if len(candidates) != 1:
                raise SystemExit(f"unexpected executable count for {asset_name}: {candidates}")

            out_dir = JNI_LIBS / abi
            out_dir.mkdir(parents=True, exist_ok=True)
            out_path = out_dir / "libstormdns_client.so"
            out_path.write_bytes(candidates[0].read_bytes())
            mode = out_path.stat().st_mode
            out_path.chmod(mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
            print(abi, "->", out_path)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
