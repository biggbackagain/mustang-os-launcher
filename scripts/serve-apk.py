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
<title>Descargar Mustang OS 0.3.6</title>
<style>body{background:#090b0f;color:#f4f5f7;font:20px system-ui;margin:0;padding:32px}
main{max-width:760px;margin:24px auto;padding:32px;border:1px solid #303540;border-radius:24px;background:#11141a}
h1{font-size:36px;margin:0 0 12px}span{color:#ff354a}p,li{line-height:1.6}small{color:#b0b5c0}
a{display:block;background:#d9253b;color:white;text-decoration:none;padding:22px;text-align:center;border-radius:16px;font-weight:bold;margin:28px 0;font-size:22px}li{margin:12px 0}
.badge{background:#1f2530;padding:4px 10px;border-radius:8px;font-size:14px;color:#ff354a;font-weight:bold}</style>
<main><h1>MUSTANG <span>OS</span> <span class="badge">v0.3.6</span></h1>
<small>Actualización de prueba sobre tus cambios de 0.3.5</small>
<a href="/MustangOS.apk" download="MustangOS-debug.apk">Descargar Mustang OS 0.3.6</a>
<ol><li>Descarga <b>MustangOS-debug.apk</b>.</li>
<li>Abre el archivo y pulsa <b>Instalar / Actualizar</b>, conservando la aplicación anterior.</li>
<li>Abre <b>MUSTANG OS</b> con el vehículo estacionado.</li></ol>
<h2>Qué comprobar</h2>
<ul>
<li><b>Música:</b> una sola orden por pulsación. La portada y Música abren CarPlay cuando usas el iPhone; una fuente Android elegida explícitamente conserva su reproductor.</li>
<li><b>Mapas:</b> el botón del panel abre CarPlay/ZLink. Los mapas del iPhone siguen dentro de ZLink; no hay una superficie de mapa incrustada verificada en este launcher.</li>
<li><b>Motor:</b> conserva la última lectura con su antigüedad y distingue datos OEM de datos Android. No convierte un dato ausente en cero ni afirma motor encendido a partir del voltaje.</li>
<li><b>GPS:</b> muestra estado de proveedores y señal; la velocidad requiere una lectura GNSS válida. Una posición de red no se usa como velocidad.</li>
<li><b>Diseño:</b> foto del Mustang en Apps, caballo junto al nombre y faros con bienvenida visual y avisos ACC compatibles.</li>
</ul>
<p><small>La compilación y las pruebas locales no sustituyen la comprobación de pausa, GPS y lecturas en tu pantalla. La integración CarPlay dentro del miniwidget sigue pendiente del contrato del proveedor.</small></p>
<h2>Enviar diagnóstico al PC</h2>
<p>En Mustang OS abre <b>Ajustes → Diagnóstico y exportación → Compatibilidad OEM → Guardar ZIP</b>. Guarda el informe con la lectura OEM activa y selecciónalo aquí:</p>
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
if(!response.ok)throw Error();result.textContent='Recibido en el PC.';
}catch(error){result.textContent='No se pudo enviar. Comprueba que el PC y la pantalla siguen en la misma red Wi-Fi.';}finally{button.disabled=false;}
};
</script>
<p><small>El enlace requiere el PC encendido y la misma red; caduca dos horas después de iniciar el servidor.</small></p>
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
                    while remaining > 0:
                        chunk = self.rfile.read(min(remaining, 65536))
                        if not chunk:
                            raise ConnectionResetError()
                        output.write(chunk)
                        remaining -= len(chunk)
            except Exception:
                destination.unlink(missing_ok=True)
                self.send_error(500)
                return
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Access-Control-Allow-Origin", f"http://{args.host}:{args.port}")
            self.end_headers()
            self.wfile.write(b'{"status":"ok"}')

        def respond(self, send_body: bool):
            path = urlsplit(self.path).path
            if path in ("", "/", "/index.html"):
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(page)))
                self.end_headers()
                if send_body:
                    self.wfile.write(page)
                return

            if path in ("/MustangOS.apk", "/MustangOS-debug.apk", "/MustangOS-0.3.5-debug.apk", "/app-debug.apk"):
                stat = apk.stat()
                self.send_response(200)
                self.send_header("Content-Type", "application/vnd.android.package-archive")
                self.send_header("Content-Disposition", 'attachment; filename="MustangOS-debug.apk"')
                self.send_header("Content-Length", str(stat.st_size))
                self.end_headers()
                if send_body:
                    with apk.open("rb") as stream:
                        shutil.copyfileobj(stream, self.wfile)
                return

            self.send_error(404)

        def log_message(self, format, *args):
            print(f"[{datetime.now().strftime('%H:%M:%S')}] {args[0]}")

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Servidor listo en http://{args.host}:{args.port}/ sirviendo {apk.name} ({apk.stat().st_size} bytes)")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()

if __name__ == "__main__":
    main()
