from __future__ import annotations

import json
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parent


def load_handbook_chunks() -> list[dict[str, Any]]:
    return json.loads((ROOT / "data" / "handbook_chunks.json").read_text(encoding="utf-8"))

