#!/usr/bin/env python3
"""e2e_permission_realrun.py 的轻量回归测试。"""

from __future__ import annotations

import unittest
from dataclasses import dataclass, field
from pathlib import Path
import sys
from typing import Any
from unittest.mock import MagicMock

sys.path.insert(0, str(Path(__file__).resolve().parent))

import e2e_permission_realrun as realrun


@dataclass
class FakeContext:
    api: Any
    login_results: dict[str, dict[str, Any]] = field(default_factory=dict)
    tokens: dict[str, str] = field(default_factory=dict)
    creations: dict[str, list[str]] = field(default_factory=lambda: {"reset": []})


class FixtureLoginRefreshTests(unittest.TestCase):
    def test_refreshes_tokens_invalidated_by_fixture_permission_grants(self) -> None:
        context = FakeContext(api=MagicMock())
        refreshed: list[str] = []

        def fake_login(ctx: FakeContext, login_key: str, username: str) -> str:
            refreshed.append(username)
            ctx.login_results[login_key] = {"accessToken": f"{username}-new"}
            ctx.tokens[login_key] = f"{username}-new"
            return f"{username}-new"

        realrun.refresh_fixture_logins_after_permission_grants(context, login_fn=fake_login)

        self.assertEqual(
            refreshed,
            [
                "U-TA1",
                "U-TA3",
                "U-TA2",
                "U-TAO1",
                "U-TAC1",
                "U-ST1",
                "U-ST2",
                "U-ST3",
                "U-M1",
                "U-STX1",
            ],
        )
        self.assertEqual(context.tokens["U-TA1"], "U-TA1-new")
        self.assertIn("fixture-logins-refreshed-after-permission-grants", context.creations["reset"])


if __name__ == "__main__":
    unittest.main()
