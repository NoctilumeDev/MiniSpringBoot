import unittest

from scripts.verify_repository_contracts import residual_hygiene_violations


class ResidualHygieneTests(unittest.TestCase):
    def test_keeps_product_assets_and_frozen_evidence(self) -> None:
        paths = [
            "demo-frontend/src/assets/control-cabin-background-v3.png",
            "docs/screenshots/404-evidence.png",
            "docs/evidence/m10/20260822-021933-failover-load.out.log",
        ]
        self.assertEqual(residual_hygiene_violations(paths, []), [])

    def test_rejects_ignored_generated_and_temporary_files(self) -> None:
        paths = [
            "debug.log",
            "mini-spring-core/target/classes/Core.class",
            "notes.md.orig",
        ]
        self.assertEqual(
            residual_hygiene_violations(paths, ["debug.log"]),
            [
                "TRACKED_IGNORED_ARTIFACT: debug.log",
                "TRACKED_GENERATED_OUTPUT: mini-spring-core/target/classes/Core.class",
                "TRACKED_TEMPORARY_FILE: notes.md.orig",
            ],
        )


if __name__ == "__main__":
    unittest.main()
