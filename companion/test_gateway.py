import hashlib
import http.client
import json
import os
from pathlib import Path
import tempfile
import threading
import unittest
from http.server import ThreadingHTTPServer

from gateway import Library, byte_range, make_handler


class GatewayTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / "switch").mkdir()
        self.path = self.root / "switch" / "Example.nsp"
        self.payload = bytes(range(256)) * 1024
        self.path.write_bytes(self.payload)
        self.library = Library(self.root)
        self.library.scan()
        self.token = "test-only-token-" + "a" * 32
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(self.library, self.token))
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.temp.cleanup()

    def request(self, path="/api/downloads", method="GET", auth=True, **headers):
        if auth:
            headers["Authorization"] = "Bearer " + self.token
        connection = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=2)
        connection.request(method, path, headers=headers)
        response = connection.getresponse()
        result = response.status, dict(response.getheaders()), response.read()
        connection.close()
        return result

    def manifest(self):
        status, _, body = self.request()
        self.assertEqual(200, status)
        return json.loads(body)

    def content_path(self):
        return "/api/downloads/" + self.manifest()["items"][0]["id"] + "/content"

    def test_auth_required_for_catalog_and_content(self):
        path = self.content_path()
        for url in ["/api/downloads", path]:
            self.assertEqual(401, self.request(url, auth=False)[0])
            self.assertEqual(401, self.request(url, auth=False, Authorization="Bearer wrong")[0])

    def test_actual_checksum_and_metadata(self):
        manifest = self.manifest()
        self.assertEqual(1, manifest["total"])
        item = manifest["items"][0]
        self.assertEqual("switch", item["platform"])
        self.assertEqual("Example.nsp", item["file_name"])
        self.assertEqual(len(self.payload), item["size_bytes"])
        self.assertEqual(hashlib.sha256(self.payload).hexdigest(), item["sha256"])
        self.assertEqual("Example", item["title"])

    def test_download_resume_checksum(self):
        path = self.content_path()
        status, headers, first = self.request(path, Range="bytes=0-1199")
        self.assertEqual(206, status)
        self.assertEqual(f"bytes 0-1199/{len(self.payload)}", headers["Content-Range"])
        status, _, rest = self.request(path, Range="bytes=1200-", **{"If-Range": headers["ETag"]})
        self.assertEqual(206, status)
        self.assertEqual(self.payload, first + rest)
        self.assertEqual(headers["ETag"], '"' + hashlib.sha256(first + rest).hexdigest() + '"')

    def test_head_and_suffix_range(self):
        path = self.content_path()
        status, headers, body = self.request(path, method="HEAD")
        self.assertEqual(200, status)
        self.assertEqual(str(len(self.payload)), headers["Content-Length"])
        self.assertEqual(b"", body)
        status, _, body = self.request(path, Range="bytes=-37")
        self.assertEqual(206, status)
        self.assertEqual(self.payload[-37:], body)

    def test_invalid_ranges_and_identity_preconditions(self):
        path = self.content_path()
        for value in ["bytes=1-0", "bytes=999999-", "bytes=-0", "bytes=1-2,4-5", "bytes=-", "items=1-2"]:
            status, headers, _ = self.request(path, Range=value)
            self.assertEqual(416, status, value)
            self.assertEqual(f"bytes */{len(self.payload)}", headers["Content-Range"])
        self.assertEqual(412, self.request(path, **{"If-Match": '"stale"'})[0])
        status, _, body = self.request(path, Range="bytes=17-", **{"If-Range": '"stale"'})
        self.assertEqual(200, status)
        self.assertEqual(self.payload, body)

    def test_replaced_file_invalidates_old_identity(self):
        path = self.content_path()
        self.path.write_bytes(b"replacement")
        self.assertEqual(404, self.request(path)[0])
        replacement = self.content_path()
        self.assertNotEqual(path, replacement)
        self.assertEqual(b"replacement", self.request(replacement)[2])

    def test_equal_size_restored_mtime_does_not_reuse_old_hash(self):
        old = self.manifest()["items"][0]
        timestamp = self.path.stat()
        self.path.write_bytes(b"x" * len(self.payload))
        os.utime(self.path, ns=(timestamp.st_atime_ns, timestamp.st_mtime_ns))
        new = self.manifest()["items"][0]
        self.assertNotEqual(old["sha256"], new["sha256"])

    def test_excludes_saves_archives_partial_symlink_and_multifile_formats(self):
        for name in ["save.sav", "Example.nsp.part", "archive.zip", "hidden.nsp.partial", ".hidden.nsp", "bad?.nsp", "CON.nsp", "bad\u0080.nsp"]:
            (self.path.parent / name).write_bytes(b"private")
        (self.path.parent / "outside.nsp").symlink_to(self.path)
        (self.root / "psx").mkdir()
        (self.root / "psx" / "disc.cue").write_text('FILE "disc.bin" BINARY')
        (self.root / "psx" / "disc.bin").write_bytes(b"data")
        (self.root / "n3ds").mkdir()
        (self.root / "n3ds" / "Cart.3ds").write_bytes(b"data")
        (self.root / "nds").symlink_to(self.root / "switch", target_is_directory=True)
        self.assertEqual(1, self.manifest()["total"])

    def test_symlink_swap_after_manifest_does_not_escape_root(self):
        path = self.content_path()
        self.path.unlink()
        self.path.symlink_to("/etc/passwd")
        self.assertEqual(404, self.request(path)[0])

    def test_queries_traversal_and_unsupported_mutations(self):
        for url in ["/api/downloads/../content", "/api/downloads/%2e%2e/content", "/etc/passwd"]:
            self.assertEqual(404, self.request(url)[0])
        self.assertEqual(400, self.request("/api/downloads?token=bad")[0])
        self.assertEqual(501, self.request(method="POST")[0])


if __name__ == "__main__":
    unittest.main()
