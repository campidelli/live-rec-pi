from __future__ import annotations

import copy
import shutil
from dataclasses import asdict
from pathlib import Path

from .config import DaemonConfig, RecordingSettings
from .formats import OutputFormat


class Storage:
    """Manages the recordings directory: sessions, files, and disk info."""

    def __init__(self, daemon_cfg: DaemonConfig) -> None:
        self._settings = copy.copy(daemon_cfg.recording)
        self._ensure_writable(Path(self._settings.output_path))
        self._storage_roots = ["/media", "/mnt", "/run/media"]

    # ---------- settings ----------

    def get_settings(self) -> dict:
        return asdict(self._settings)

    def update_settings(self, **kwargs) -> None:
        if "output_path" in kwargs:
            target = Path(kwargs["output_path"]).expanduser()
            if not target.is_absolute():
                raise ValueError("output_path must be absolute")
            self._ensure_writable(target)
            kwargs["output_path"] = str(target)

        if "format" in kwargs:
            try:
                OutputFormat(kwargs["format"])
            except ValueError:
                valid = [f.value for f in OutputFormat]
                raise ValueError(f"Invalid format {kwargs['format']!r}. Valid options: {valid}")

        for key, value in kwargs.items():
            if not hasattr(self._settings, key):
                raise ValueError(f"Unknown setting: {key!r}")
            setattr(self._settings, key, value)

    # ---------- active paths ----------

    @property
    def output_path(self) -> Path:
        return Path(self._settings.output_path)

    def session_dir(self, session: str) -> Path:
        return self.output_path / session

    # ---------- storage options ----------

    def storage_options(self) -> list[dict]:
        candidates = [self.output_path]
        for root in self._storage_roots:
            root_path = Path(root)
            if not root_path.exists():
                continue
            for child in root_path.iterdir():
                if child.is_dir():
                    candidates.append(child / "recordings")

        seen: set[str] = set()
        options: list[dict] = []
        for path in candidates:
            resolved = str(path.resolve())
            if resolved in seen:
                continue
            seen.add(resolved)
            if not self._is_writable(path):
                continue
            info = self._disk_info(path)
            info["label"] = (
                "Local Storage"
                if path.resolve() == self.output_path.resolve()
                else path.parent.name or str(path.parent)
            )
            options.append(info)

        return sorted(options, key=lambda x: x["label"].lower())

    def free_space(self) -> dict:
        return self._disk_info(self.output_path)

    # ---------- session / file access ----------

    def list_sessions(self) -> list[dict]:
        sessions = []
        if not self.output_path.exists():
            return sessions
        for session_dir in sorted(self.output_path.iterdir()):
            if not session_dir.is_dir():
                continue
            wav_files = sorted(session_dir.glob(f"*.{self._settings.format}"))
            if not wav_files:
                continue
            sessions.append({
                "session": session_dir.name,
                "files": [
                    {"name": f.name, "size_bytes": f.stat().st_size}
                    for f in wav_files
                ],
            })
        return sessions

    def rename_file(self, session: str, old: str, new: str) -> None:
        ext = f".{self._settings.format}"
        if not new.endswith(ext):
            new += ext
        src = self.session_dir(session) / old
        dst = self.session_dir(session) / new
        if not src.exists():
            raise FileNotFoundError(old)
        if dst.exists():
            raise FileExistsError(f"Target already exists: {new}")
        src.rename(dst)

    def delete_file(self, session: str, filename: str) -> None:
        path = self.session_dir(session) / filename
        if not path.exists():
            raise FileNotFoundError(filename)
        path.unlink()

    # ---------- helpers ----------

    def _ensure_writable(self, path: Path) -> None:
        if not self._is_writable(path):
            raise OSError(f"Path is not writable: {path}")

    @staticmethod
    def _is_writable(path: Path) -> bool:
        try:
            path.mkdir(parents=True, exist_ok=True)
            probe = path / ".write_test"
            probe.touch(exist_ok=True)
            probe.unlink(missing_ok=True)
            return True
        except Exception:
            return False

    @staticmethod
    def _disk_info(path: Path) -> dict:
        usage = shutil.disk_usage(path)
        return {
            "path": str(path),
            "free_bytes": usage.free,
            "total_bytes": usage.total,
        }
