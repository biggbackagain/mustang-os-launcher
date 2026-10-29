"""Serve only the selected APK on a chosen LAN interface, for at most two hours."""
from argparse import ArgumentParser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from time import monotonic
from urllib.parse import urlsplit
from datetime import datetime, timezone
import json
import re
import secrets
import shutil
import uuid

PAGE = """<!doctype html><html lang="es"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Mustang OS 0.8.1 · Render 3D y CarPlay</title>
<style>body{background:#061b37;color:#f4f5f7;font:20px system-ui;margin:0;padding:32px}
main{max-width:760px;margin:24px auto;padding:32px;border:1px solid #303e57;border-radius:24px;background:#0b2342}
h1{font-size:36px;margin:0 0 12px}span{color:#93d8ff}p,li{line-height:1.6}small{color:#b0b5c0}
a{display:block;background:#93d8ff;color:#07131c;text-decoration:none;padding:22px;text-align:center;border-radius:16px;font-weight:bold;margin:28px 0;font-size:22px}li{margin:12px 0}
.badge{background:#1f2530;padding:4px 10px;border-radius:8px;font-size:14px;color:#93d8ff;font-weight:bold}summary{cursor:pointer;padding:16px 0}</style>
<main><h1>MUSTANG <span>OS</span> <span class="badge">v0.8.1</span></h1>
<small>Render 3D · portada y guía de CarPlay · tablero nuevo</small>
<a href="/MustangOS.apk?v=0.8.1" download="MustangOS.apk">Descargar Mustang OS 0.8.1</a>
<ol><li>Descarga <b>MustangOS.apk</b>.</li>
<li>Abre el archivo y pulsa <b>Instalar / Actualizar</b>, conservando la aplicación anterior.</li>
<li>Abre <b>MUSTANG OS</b> con el vehículo estacionado.</li></ol>
OFFLINE_PACK
<h2>Qué incluye</h2>
<ul>
<li><b>Render 3D del Mustang:</b> pintura con reflejos de estudio, faros y calaveras que se encienden, puertas animadas y rines que giran solo con velocidad real. Arrastra el coche para girarlo; doble toque para volver. Si el equipo no soporta OpenGL ES 2.0 se muestra la foto del perfil.</li>
<li><b>Portada de CarPlay:</b> ahora acepta JPEG y PNG, espera hasta 20 s a que llegue la imagen y sigue leyendo mientras CarPlay está al frente. Mantén activo <b>Ajustes → Datos de CarPlay</b>.</li>
<li><b>Guía de CarPlay:</b> con una ruta activa en el iPhone, la tarjeta Navegación muestra flecha, distancia, instrucción y llegada. Tócala para abrir el mapa de CarPlay. Depende de que ZLink publique la ruta.</li>
<li><b>Barra sobre CarPlay:</b> se reduce sola a un caballo en el borde para dejar ver el mapa. Tócalo para abrirla, arrástralo para moverlo.</li>
<li><b>Tablero:</b> portada grande teñida con su color, coche a sangre con velocidad y RPM, tarjeta de navegación más ancha.</li>
<li><b>Si falta portada o guía:</b> abre <b>Ajustes → Comprobar metadatos de ZLink</b> y exporta el diagnóstico sin detener la lectura; envíalo con el formulario de abajo.</li>
</ul>
<p><small>El vídeo del mapa de CarPlay sigue en la ventana de ZLink. La APK es release sin depuración, con la misma firma de la versión anterior para actualizar sin perder datos.</small></p>
<details open><summary>Enviar diagnóstico o APK al PC</summary>
<p>Puedes enviar un diagnóstico nuevo o un APK del equipo. El archivo se guarda para análisis; no se instala ni ejecuta en el PC.</p>
<p>Con CarPlay conectado, deja una canción reproduciéndose y regresa con Home. Abre <b>Ajustes → Diagnóstico y exportación → Compatibilidad OEM → Guardar ZIP</b> y selecciona aquí el archivo nuevo:</p>
<input id="report" type="file" accept=".zip,.apk,application/zip,application/vnd.android.package-archive" style="font-size:18px;max-width:100%">
<button id="send" style="font-size:20px;background:#1d222b;color:white;padding:20px;border:1px solid #4b5260;border-radius:12px;margin-top:16px">Enviar archivo al PC</button>
<p id="result" role="status"></p>
<script>
document.getElementById('send').onclick=async()=>{
const file=document.getElementById('report').files[0], result=document.getElementById('result'), button=document.getElementById('send');
if(!file){result.textContent='Selecciona un archivo ZIP o APK.';return;}
if(file.size>134217728){result.textContent='El archivo supera 128 MiB.';return;}
button.disabled=true;result.textContent='Enviando por la red local…';
try{const response=await fetch('/upload',{method:'POST',headers:{'Content-Type':'application/zip','X-Mustang-Upload-Token':'UPLOAD_TOKEN'},body:file});
if(!response.ok)throw Error();result.textContent='Recibido en el PC.';
}catch(error){result.textContent='No se pudo enviar. Comprueba que el PC y la pantalla siguen en la misma red Wi-Fi.';}finally{button.disabled=false;}
};
</script>
</details>
<p><small>El enlace requiere el PC encendido y la misma red; caduca dos horas después de iniciar el servidor.</small></p>
</main></html>"""


def requested_range(value: str | None, size: int):
    """Return one inclusive byte range; reject invalid/multiple ranges."""
    if value is None:
        return 0, size - 1, False
    match = re.fullmatch(r"bytes=(\d*)-(\d*)", value)
    if not match or not any(match.groups()) or size < 1:
        raise ValueError("Invalid range")
    first, last = match.groups()
    if not first:
        count = int(last)
        if count <= 0: raise ValueError("Invalid suffix")
        return max(0, size - count), size - 1, True
    start = int(first)
    end = min(int(last), size - 1) if last else size - 1
    if start >= size or start > end:
        raise ValueError("Unsatisfiable range")
    return start, end, True


def main():
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("--host", required=True, help="IPv4 local del PC")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--apk", type=Path, default=Path(__file__).resolve().parents[1] / "output" / "MustangOS.apk")
    parser.add_argument("--uploads", type=Path, default=Path(__file__).resolve().parents[1] / "output" / "device-reports")
    parser.add_argument("--offline-map", type=Path, help="Paquete MAP de México preparado para descargar por LAN")
    args = parser.parse_args()

    apk = args.apk.resolve(strict=True)

    offline_map = args.offline_map.resolve(strict=True) if args.offline_map else None
    if offline_map:
        with offline_map.open('rb') as stream:
            if stream.read(20) != b'mapsforge binary OSM': raise ValueError('El paquete no es un mapa Mapsforge')
        if not 20 <= offline_map.stat().st_size <= 2_000_000_000: raise ValueError('Tamaño de mapa no permitido')
        pack_section = f'''<h2>México sin conexión</h2>
<p>Paquete completo de cartografía para el panel: {offline_map.stat().st_size / 1048576:.0f} MiB. Descárgalo una vez y en Mustang OS entra a <b>Ajustes → Mapas sin conexión → Importar .map</b>. Después podrás explorar el mapa sin Internet.</p>
<a href="/maps/mexico.map" download="Mexico-MustangOS.map">Descargar mapa de México</a>
<p><small>Datos OpenStreetMap, licencia ODbL; paquete distribuido por Mapsforge. Es cartografía offline; no incorpora el vídeo de CarPlay ni cálculo de rutas.</small></p>'''
    else:
        pack_section = ''

    token = secrets.token_urlsafe(24)
    page = PAGE.replace("UPLOAD_TOKEN", token).replace("OFFLINE_PACK", pack_section).encode("utf-8")
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
                self.send_header("Cache-Control", "no-store")
                self.send_header("Content-Length", str(len(page)))
                self.end_headers()
                if send_body:
                    self.wfile.write(page)
                return

            if path in ("/MustangOS.apk", "/MustangOS-debug.apk", "/MustangOS-0.3.5-debug.apk", "/app-debug.apk"):
                stat = apk.stat()
                self.send_response(200)
                self.send_header("Content-Type", "application/vnd.android.package-archive")
                self.send_header("Cache-Control", "no-store")
                self.send_header("Content-Disposition", 'attachment; filename="MustangOS.apk"')
                self.send_header("Content-Length", str(stat.st_size))
                self.end_headers()
                if send_body:
                    with apk.open("rb") as stream:
                        shutil.copyfileobj(stream, self.wfile)
                return

            if path == "/maps/mexico.map" and offline_map:
                size = offline_map.stat().st_size
                try:
                    start, end, partial = requested_range(self.headers.get('Range'), size)
                except ValueError:
                    self.send_response(416)
                    self.send_header('Content-Range', f'bytes */{size}')
                    self.send_header('Content-Length', '0')
                    self.end_headers()
                    return
                self.send_response(206 if partial else 200)
                self.send_header('Content-Type', 'application/octet-stream')
                self.send_header('Content-Disposition', 'attachment; filename="Mexico-MustangOS.map"')
                self.send_header('Cache-Control', 'no-store')
                self.send_header('Accept-Ranges', 'bytes')
                self.send_header('Content-Length', str(end - start + 1))
                if partial: self.send_header('Content-Range', f'bytes {start}-{end}/{size}')
                self.end_headers()
                if send_body:
                    with offline_map.open('rb') as stream:
                        stream.seek(start)
                        remaining = end - start + 1
                        while remaining:
                            chunk = stream.read(min(65536, remaining))
                            if not chunk: break
                            self.wfile.write(chunk)
                            remaining -= len(chunk)
                return

            self.send_error(404)

        def log_message(self, format, *args):
            print(f"[{datetime.now().strftime('%H:%M:%S')}] {args[0]}")

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    server.timeout = 1.0
    deadline = monotonic() + 2 * 60 * 60
    print(f"Servidor listo en http://{args.host}:{args.port}/ sirviendo {apk.name} ({apk.stat().st_size} bytes)")
    try:
        while monotonic() < deadline:
            server.handle_request()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()

if __name__ == "__main__":
    main()
