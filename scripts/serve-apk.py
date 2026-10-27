"""Serve only the debug APK on a chosen LAN interface, for at most two hours."""
from argparse import ArgumentParser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from time import monotonic
from urllib.parse import urlsplit
from datetime import datetime, timezone
import json
import secrets
import shutil
import uuid

PAGE = """<!doctype html><html lang="es"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Descargar Mustang OS</title>
<style>body{background:#090b0f;color:#f4f5f7;font:20px system-ui;margin:0;padding:32px}
main{max-width:720px;margin:24px auto;padding:32px;border:1px solid #303540;border-radius:24px;background:#11141a}
h1{font-size:36px;margin:0 0 12px}span{color:#ff354a}p,li{line-height:1.6}small{color:#b0b5c0}
a{display:block;background:#d9253b;color:white;text-decoration:none;padding:22px;text-align:center;border-radius:16px;font-weight:bold;margin:28px 0}li{margin:12px 0}</style>
<main><h1>MUSTANG <span>OS</span></h1><small>Actualización de prueba 0.2.0</small>
<p>Instálala como una aplicación normal para probarla en tu pantalla Android.</p>
<a href="/MustangOS.apk" download="MustangOS-debug.apk">Descargar Mustang OS</a>
<ol><li>Descarga y abre <b>MustangOS-debug.apk</b>.</li>
<li>Si Android lo solicita, permite a este navegador <b>instalar aplicaciones desconocidas</b> y confirma <b>Instalar</b>.</li>
<li>Abre <b>MUSTANG OS</b> desde Apps. Para la primera prueba, conserva tu launcher habitual.</li>
<li>CarPlay, radio, teléfono y música detectan las apps de tu firmware cuando están instaladas.</li></ol>
<h2>Investigar datos MCU y el widget CarPlay</h2>
<p>En Mustang OS: <b>Ajustes → Diagnóstico y exportación → Compatibilidad OEM → Guardar ZIP</b>. Guárdalo en Descargas y selecciónalo aquí.</p>
<p><small>El ZIP contiene diagnóstico y código estático de apps OEM. No incluye sus datos privados. Esta versión aún no incrusta CarPlay ni lee puertas o temperatura MCU: el ZIP permite identificar las interfaces reales.</small></p>
<input id="report" type="file" accept=".zip,application/zip" style="font-size:18px;max-width:100%">
<button id="send" style="font-size:20px;background:#1d222b;color:white;padding:20px;border:1px solid #4b5260;border-radius:12px;margin-top:16px">Enviar diagnóstico al PC</button>
<p id="result" role="status"></p>
<script>
document.getElementById('send').onclick=async()=>{
const file=document.getElementById('report').files[0], result=document.getElementById('result'), button=document.getElementById('send');
if(!file){result.textContent='Selecciona primero el ZIP de compatibilidad.';return;}
if(file.size>134217728){result.textContent='El archivo supera 128 MiB.';return;}
button.disabled=true;result.textContent='Enviando por la red local…';
try{const response=await fetch('/upload',{method:'POST',headers:{'Content-Type':'application/zip','X-Mustang-Upload-Token':'UPLOAD_TOKEN'},body:file});
if(!response.ok)throw Error();result.textContent='Recibido en el PC. Ya podemos analizar las interfaces reales.';
}catch(error){result.textContent='No se pudo enviar. Comprueba que el PC y la pantalla siguen en la misma red.';}finally{button.disabled=false;}
};
</script>
<p><small>Prueba con el vehículo estacionado. La música del iPhone solo aparecerá si ZLink expone una sesión compatible. No requiere depuración USB ni contraseña de fábrica.</small></p>
</main></html>"""


def main():
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("--host", required=True, help="IPv4 local del PC")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--apk", type=Path, default=Path(__file__).resolve().parents[1] / "output" / "MustangOS-debug.apk")
    parser.add_argument("--uploads", type=Path, default=Path(__file__).resolve().parents[1] / "output" / "device-reports")
    args = parser.parse_args()
    apk = args.apk.resolve(strict=True)
    token = secrets.token_urlsafe(24)
    page = PAGE.replace("UPLOAD_TOKEN", token).encode("utf-8")
    upload_dir = args.uploads.resolve()

    class Handler(BaseHTTPRequestHandler):
        def do_HEAD(self):
            self.respond(False)

        def do_GET(self):
            self.respond(True)

        def do_POST(self):
            if urlsplit(self.path).path != "/upload":
                self.send_error(404); return
            if not secrets.compare_digest(self.headers.get("X-Mustang-Upload-Token", ""), token):
                self.send_error(403); return
            origin = self.headers.get("Origin")
            if origin is not None and origin != f"http://{args.host}:{args.port}":
                self.send_error(403); return
            try:
                size = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                self.send_error(400); return
            if not 4 <= size <= 128 * 1024 * 1024:
                self.send_error(413); return
            self.connection.settimeout(30)
            header = self.rfile.read(4)
            if header not in (b"PK\x03\x04", b"PK\x05\x06"):
                self.send_error(415); return
            upload_dir.mkdir(parents=True, exist_ok=True)
            destination = upload_dir / (datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:8] + ".zip")
            try:
                with destination.open("xb") as output:
                    output.write(header)
                    remaining = size - 4
                    while remaining:
                        chunk = self.rfile.read(min(65536, remaining))
                        if not chunk: raise ConnectionError("Incomplete upload")
                        output.write(chunk); remaining -= len(chunk)
                print("Paquete de compatibilidad recibido", flush=True)
                body = json.dumps({"ok": True}).encode("utf-8")
                self.send_response(200); self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)
            except (OSError, ConnectionError):
                destination.unlink(missing_ok=True)
                self.send_error(400)

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
            self.send_header("Content-Length", str(apk.stat().st_size if is_apk else len(page)))
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
                        self.wfile.write(page)
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
