"""Verify the actual HTTP download/upload boundary using isolated temporary files."""
import hashlib
import io
from pathlib import Path
import re
import runpy
import socket
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch
import urllib.error
import urllib.request
import zipfile


class DownloadServerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.base_temp = (Path(__file__).resolve().parents[1] / ".downloads" / "server-tests").resolve()
        cls.base_temp.mkdir(parents=True, exist_ok=True)
        cls.temp = tempfile.TemporaryDirectory(prefix="run-", dir=cls.base_temp)
        cls.root = Path(cls.temp.name)
        if not cls.root.resolve().is_relative_to(cls.base_temp): raise RuntimeError("Temporary path outside workspace")
        cls.apk = cls.root / "test.apk"
        cls.apk.write_bytes(b"test APK bytes only")
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0)); cls.port = sock.getsockname()[1]
        cls.base = f"http://127.0.0.1:{cls.port}"
        cls.proc = subprocess.Popen([sys.executable, str(Path(__file__).with_name("serve-apk.py")), "--host", "127.0.0.1", "--port", str(cls.port), "--apk", str(cls.apk), "--uploads", str(cls.root / "uploads")], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        for _ in range(100):
            try:
                with urllib.request.urlopen(cls.base, timeout=1) as response: cls.page = response.read().decode()
                break
            except urllib.error.URLError: time.sleep(0.05)
        else: raise RuntimeError("Server did not start")
        cls.token = re.search(r"'X-Mustang-Upload-Token':'([^']+)'", cls.page)[1]

    @classmethod
    def tearDownClass(cls):
        cls.proc.terminate(); cls.proc.wait(timeout=5)
        if not cls.root.resolve().is_relative_to(cls.base_temp): raise RuntimeError("Refusing cleanup outside test directory")
        cls.temp.cleanup()

    def post(self, data, token=None, origin=None):
        headers = {"Content-Type": "application/zip"}
        if token is not None: headers["X-Mustang-Upload-Token"] = token
        if origin is not None: headers["Origin"] = origin
        return urllib.request.urlopen(urllib.request.Request(self.base + "/upload", data=data, headers=headers), timeout=5)

    def test_apk_download_is_identical(self):
        with urllib.request.urlopen(self.base + "/MustangOS.apk") as response:
            self.assertEqual("no-store", response.headers.get("Cache-Control"))
            self.assertIn('filename="MustangOS.apk"', response.headers.get("Content-Disposition", ""))
            self.assertEqual(hashlib.sha256(response.read()).digest(), hashlib.sha256(self.apk.read_bytes()).digest())

    def test_server_closes_after_two_hours_without_waiting_in_real_time(self):
        script = Path(__file__).with_name("serve-apk.py")
        with patch.object(sys, "argv", [str(script), "--host", "127.0.0.1", "--apk", str(self.apk)]), \
                patch("http.server.ThreadingHTTPServer") as server_type, \
                patch("time.monotonic", side_effect=[10.0, 11.0, 7210.0]):
            runpy.run_path(str(script), run_name="__main__")
        server_type.return_value.handle_request.assert_called_once()
        server_type.return_value.server_close.assert_called_once()

    def test_other_files_are_not_served(self):
        with self.assertRaises(urllib.error.HTTPError) as caught: urllib.request.urlopen(self.base + "/device-reports/private.zip")
        self.assertEqual(404, caught.exception.code)

    def test_upload_requires_page_token(self):
        with self.assertRaises(urllib.error.HTTPError) as caught: self.post(b"PK\x05\x06")
        self.assertEqual(403, caught.exception.code)

    def test_cross_origin_upload_rejected(self):
        with self.assertRaises(urllib.error.HTTPError) as caught: self.post(b"PK\x05\x06", self.token, "https://other.example")
        self.assertEqual(403, caught.exception.code)

    def test_non_zip_rejected(self):
        with self.assertRaises(urllib.error.HTTPError) as caught: self.post(b"not a zip", self.token)
        self.assertEqual(415, caught.exception.code)

    def test_zip_stored_without_extracting_or_executing(self):
        before = set((self.root / "uploads").glob("*.zip"))
        payload = io.BytesIO()
        with zipfile.ZipFile(payload, "w") as archive: archive.writestr("../must-not-extract.txt", "test")
        with self.post(payload.getvalue(), self.token, self.base) as response: self.assertEqual(200, response.status)
        received = list(set((self.root / "uploads").glob("*.zip")) - before)
        self.assertEqual(1, len(received))
        self.assertEqual(payload.getvalue(), received[0].read_bytes())
        self.assertFalse((self.root / "must-not-extract.txt").exists())

    def test_apk_can_be_received_as_an_opaque_archive_without_execution(self):
        self.assertIn('.apk,application/zip,application/vnd.android.package-archive', self.page)
        before = set((self.root / "uploads").glob("*.zip"))
        payload = io.BytesIO()
        with zipfile.ZipFile(payload, "w") as archive:
            archive.writestr("AndroidManifest.xml", "fixture manifest")
            archive.writestr("classes.dex", "fixture static code")
        with self.post(payload.getvalue(), self.token, self.base) as response:
            self.assertEqual(200, response.status)
        received = list(set((self.root / "uploads").glob("*.zip")) - before)
        self.assertEqual(1, len(received))
        self.assertEqual(payload.getvalue(), received[0].read_bytes())
        self.assertFalse((self.root / "classes.dex").exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
