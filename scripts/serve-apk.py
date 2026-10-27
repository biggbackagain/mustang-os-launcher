"""Serve only the debug APK on a chosen LAN interface, for at most two hours."""
from argparse import ArgumentParser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from time import monotonic
from urllib.parse import urlsplit
import shutil

PAGE = """<!doctype html><html lang="es"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Descargar Mustang OS</title>
<style>body{background:#090b0f;color:#f4f5f7;font:20px system-ui;margin:0;padding:32px}
main{max-width:720px;margin:24px auto;padding:32px;border:1px solid #303540;border-radius:24px;background:#11141a}
h1{font-size:36px;margin:0 0 12px}span{color:#ff354a}p,li{line-height:1.6}small{color:#b0b5c0}
a{display:block;background:#d9253b;color:white;text-decoration:none;padding:22px;text-align:center;border-radius:16px;font-weight:bold;margin:28px 0}li{margin:12px 0}</style>
<main><h1>MUSTANG <span>OS</span></h1><small>Versión de prueba 0.1.0 · APK de 2.46 MiB</small>
<p>Instálala como una aplicación normal para probarla en tu pantalla Android.</p>
<a href="/MustangOS.apk" download="MustangOS-debug.apk">Descargar Mustang OS</a>
<ol><li>Descarga y abre <b>MustangOS-debug.apk</b>.</li>
<li>Si Android lo solicita, permite a este navegador <b>instalar aplicaciones desconocidas</b> y confirma <b>Instalar</b>.</li>
<li>Abre <b>MUSTANG OS</b> desde Apps. Para la primera prueba, conserva tu launcher habitual.</li>
<li>Pulsa <b>CarPlay</b> y selecciona tu ZLink instalado. En <b>Ajustes</b> puedes elegir radio, teléfono y mapas.</li></ol>
<p><small>Prueba con el vehículo estacionado. La música del iPhone solo aparecerá si ZLink expone una sesión compatible. No requiere depuración USB ni contraseña de fábrica.</small></p>
</main></html>""".encode("utf-8")


def main():
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("--host", required=True, help="IPv4 local del PC")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--apk", type=Path, default=Path(__file__).resolve().parents[1] / "output" / "MustangOS-debug.apk")
    args = parser.parse_args()
    apk = args.apk.resolve(strict=True)

    class Handler(BaseHTTPRequestHandler):
        def do_HEAD(self):
            self.respond(False)

        def do_GET(self):
            self.respond(True)

        def log_message(self, format, *values):
            # No client addresses, cookies, query strings or device identifiers are logged.
            pass

        def respond(self, body):
            route = urlsplit(self.path).path
            if route not in ("/", "/MustangOS.apk"):
                self.send_error(404)
                return
            is_apk = route == "/MustangOS.apk"
            self.send_response(200)
            self.send_header("Content-Type", "application/vnd.android.package-archive" if is_apk else "text/html; charset=utf-8")
            self.send_header("Content-Length", str(apk.stat().st_size if is_apk else len(PAGE)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            if is_apk:
                self.send_header("Content-Disposition", 'attachment; filename="MustangOS-debug.apk"')
            self.end_headers()
            if body:
                try:
                    if is_apk:
                        with apk.open("rb") as source:
                            shutil.copyfileobj(source, self.wfile)
                    else:
                        self.wfile.write(PAGE)
                except (BrokenPipeError, ConnectionResetError):
                    pass

    with ThreadingHTTPServer((args.host, args.port), Handler) as server:
        server.timeout = 1
        print(f"Descarga local: http://{args.host}:{args.port}/", flush=True)
        deadline = monotonic() + 7200
        while monotonic() < deadline:
            server.handle_request()


if __name__ == "__main__":
    main()
