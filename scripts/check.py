#!/usr/bin/env python3
"""Run the canonical local SDK gate from the Raims Developer Toolkit."""
from pathlib import Path
import runpy
import sys

sys.dont_write_bytecode = True


def main():
    sdk = Path(__file__).resolve().parent.parent
    workspace = next((candidate for candidate in (sdk.parent, sdk.parent.parent) if (candidate / "Developer-Toolkit/scripts/release-preflight/java_sdk.py").is_file()), sdk.parent)
    validator = workspace / "Developer-Toolkit/scripts/release-preflight/java_sdk.py"
    if not validator.is_file():
        raise RuntimeError("O validador canônico do Developer Toolkit não está disponível. Consulte CONTRIBUTING.md.")
    original_arguments = sys.argv
    try:
        sys.argv = [str(validator), "--source", str(sdk), "--workspace", str(workspace)] + original_arguments[1:]
        runpy.run_path(str(validator), run_name="__main__")
    finally:
        sys.argv = original_arguments
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, RuntimeError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
