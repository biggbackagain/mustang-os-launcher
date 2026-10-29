package com.mustangos.launcher.render

/** GLSL ES 1.00 shared by the device renderer and the desktop WebGL preview used during development. */
object MustangShaders {
    const val CAR_VERTEX = """
attribute vec3 aPos;
attribute vec3 aNormal;
uniform mat4 uModel;
uniform mat4 uViewProj;
uniform mat3 uNormalMat;
uniform float uMirror;
varying vec3 vWorld;
varying vec3 vNormal;
varying vec3 vObj;
varying vec3 vObjNormal;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    vec3 n = uNormalMat * aNormal;
    if (uMirror > 0.5) { w.y = -w.y; n.y = -n.y; }
    vObj = aPos;
    vObjNormal = aNormal;
    vWorld = w.xyz;
    vNormal = n;
    gl_Position = uViewProj * w;
}
"""

    /** Shared studio environment and helpers. */
    private const val COMMON = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
vec3 env(vec3 r) {
    float y = r.y;
    vec3 c = mix(vec3(0.010, 0.012, 0.016), vec3(0.055, 0.065, 0.085), smoothstep(-0.35, 0.0, y));
    c = mix(c, vec3(0.16, 0.18, 0.22), smoothstep(0.02, 0.7, y));
    // Large overhead softbox and two long strip lights: the reflections that shape automotive paint.
    float box = smoothstep(0.78, 0.86, y) * (1.0 - smoothstep(0.42, 0.50, abs(r.x))) * (1.0 - smoothstep(0.55, 0.62, abs(r.z)));
    c += vec3(2.4, 2.45, 2.5) * box;
    float az = atan(r.z, r.x);
    float band = smoothstep(-0.06, 0.08, y) * (1.0 - smoothstep(0.42, 0.62, y));
    float s1 = 1.0 - smoothstep(0.035, 0.075, abs(az - 1.05));
    float s2 = 1.0 - smoothstep(0.035, 0.075, abs(az + 2.05));
    float s3 = 1.0 - smoothstep(0.05, 0.10, abs(az - 2.75));
    c += vec3(1.5, 1.55, 1.6) * band * (s1 + s2) + vec3(0.6, 0.66, 0.75) * band * s3;
    float hz = (y - 0.025) / 0.03;
    c += vec3(0.45, 0.5, 0.58) * exp(-hz * hz);
    return c;
}
vec3 tonemap(vec3 x) {
    x = (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14);
    return pow(clamp(x, 0.0, 1.0), vec3(1.0 / 2.2));
}
float rbox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
float inside(float d, float aa) { return 1.0 - smoothstep(-aa, aa, d); }
"""

    const val CAR_FRAGMENT = COMMON + """
uniform vec3 uCam;
uniform vec3 uBase;
uniform float uMat;
uniform float uLights;
uniform float uMirror;
uniform float uStyle;
uniform float uAlpha;
uniform vec4 uBodyX;     // frontX, rearX, cowlX, deckX
uniform vec4 uBodyY;     // bottom, belt, halfWidth, axle
uniform vec2 uDoor;      // doorFrontX, doorRearX
varying vec3 vWorld;
varying vec3 vNormal;
varying vec3 vObj;
varying vec3 vObjNormal;

void main() {
    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCam - vWorld);
    if (dot(N, V) < 0.0) N = -N;
    vec3 on = normalize(vObjNormal);
    vec3 base = uBase;
    float clear = 0.0;      // clear-coat reflection strength
    float metal = 0.0;      // base-tinted environment reflection
    float shine = 40.0;     // primary highlight exponent
    float specK = 0.25;
    vec3 emit = vec3(0.0);
    float aa = 0.006;
    float m = uMat;
    float ax = abs(vObj.z);
    if (m < 0.5) {
        // Body paint with per-pixel details in body coordinates.
        clear = 1.0; metal = 0.18; shine = 60.0; specK = 0.45;
        float fx = uBodyX.x, rx = uBodyX.y, cowl = uBodyX.z, deck = uBodyX.w;
        float bottom = uBodyY.x, belt = uBodyY.y, hw = uBodyY.z;
        vec2 fz = vec2(ax, vObj.y);
        float gap = 0.0;
        // Panel gaps: doors, hood and deck lid.
        float sideFace = smoothstep(0.55, 0.75, abs(on.z));
        float doorBand = step(bottom + 0.11, vObj.y) * step(vObj.y, belt + 0.01) * sideFace;
        gap = max(gap, inside(abs(vObj.x - uDoor.x) - 0.0025, 0.002) * doorBand);
        gap = max(gap, inside(abs(vObj.x - uDoor.y) - 0.0025, 0.002) * doorBand);
        gap = max(gap, inside(abs(vObj.y - (bottom + 0.115)) - 0.0025, 0.002) * step(uDoor.y, vObj.x) * step(vObj.x, uDoor.x) * sideFace);
        float topFace = smoothstep(0.5, 0.8, on.y);
        gap = max(gap, inside(abs(vObj.x - (cowl + 0.03)) - 0.0025, 0.002) * step(ax, hw * 0.72) * topFace);
        gap = max(gap, inside(abs(ax - hw * 0.70) - 0.0025, 0.002) * step(cowl + 0.03, vObj.x) * step(vObj.x, fx - 0.12) * topFace);
        gap = max(gap, inside(abs(vObj.x - (deck - 0.04)) - 0.0025, 0.002) * step(ax, hw * 0.72) * topFace);
        gap = max(gap, inside(abs(ax - hw * 0.70) - 0.0025, 0.002) * step(rx + 0.10, vObj.x) * step(vObj.x, deck - 0.04) * topFace);
        base = mix(base, vec3(0.004), gap);
        clear *= 1.0 - gap * 0.9;
        // Front fascia.
        float front = smoothstep(0.15, 0.35, on.x) * step(fx - 0.55, vObj.x);
        if (front > 0.0) {
            float grille, lamp, intake, emblem;
            vec2 lampC; vec2 lampB;
            if (uStyle < 0.5) {          // classic: round lamps in a wide grille, chrome bumper
                grille = inside(rbox(fz - vec2(0.0, 0.60), vec2(0.62, 0.11), 0.05), aa);
                lampC = vec2(0.66, 0.63); lampB = vec2(0.085, 0.085);
                lamp = inside(length(fz - lampC) - 0.085, aa);
                intake = 0.0; emblem = inside(rbox(fz - vec2(0.0, 0.60), vec2(0.07, 0.04), 0.02), aa);
            } else if (uStyle < 1.5) {   // fox: rectangular lamps
                grille = inside(rbox(fz - vec2(0.0, 0.58), vec2(0.20, 0.05), 0.01), aa);
                lampC = vec2(0.52, 0.60); lampB = vec2(0.22, 0.06);
                lamp = inside(rbox(fz - lampC, lampB, 0.01), aa);
                intake = inside(rbox(fz - vec2(0.0, 0.32), vec2(0.50, 0.04), 0.02), aa); emblem = 0.0;
            } else if (uStyle < 2.5) {   // SN95
                grille = inside(rbox(fz - vec2(0.0, 0.52), vec2(0.30, 0.07), 0.06), aa);
                lampC = vec2(0.62, 0.62); lampB = vec2(0.20, 0.07);
                lamp = inside(rbox(fz - lampC, lampB, 0.05), aa);
                intake = inside(rbox(fz - vec2(0.0, 0.30), vec2(0.42, 0.05), 0.03), aa);
                emblem = inside(rbox(fz - vec2(0.0, 0.52), vec2(0.07, 0.035), 0.02), aa);
            } else if (uStyle < 3.5) {   // S197: tall grille ending in the lamps
                grille = inside(rbox(fz - vec2(0.0, 0.615), vec2(0.47, 0.125), 0.06), aa);
                lampC = vec2(0.69, 0.665); lampB = vec2(0.19, 0.085);
                lamp = inside(rbox(fz - lampC, lampB, 0.06), aa);
                intake = inside(rbox(fz - vec2(0.0, 0.335), vec2(0.50, 0.065), 0.04), aa);
                emblem = inside(rbox(fz - vec2(0.0, 0.63), vec2(0.10, 0.04), 0.02), aa);
            } else {                      // S550 / S650: trapezoid grille, slim lamps
                float trap = rbox(vec2(ax * (1.0 + (vObj.y - 0.5) * 0.9), vObj.y) - vec2(0.0, 0.52), vec2(0.52, 0.15), 0.07);
                grille = inside(trap, aa);
                lampC = vec2(0.70, 0.70); lampB = vec2(0.17, 0.045);
                lamp = inside(rbox(fz - lampC, lampB, 0.04), aa);
                intake = inside(rbox(fz - vec2(0.0, 0.27), vec2(0.40, 0.04), 0.03), aa);
                emblem = inside(rbox(fz - vec2(0.0, 0.54), vec2(0.11, 0.045), 0.02), aa);
            }
            grille *= front; intake *= front; emblem *= front;
            lamp *= smoothstep(0.0, 0.25, on.x + 0.05) * step(fx - 0.55, vObj.x);
            // Honeycomb mesh.
            vec2 h = vec2(vObj.z * 26.0, vObj.y * 30.0);
            h.x += step(1.0, mod(floor(h.y), 2.0)) * 0.5;
            float cell = length(fract(h) - 0.5);
            vec3 mesh = mix(vec3(0.006), vec3(0.035), smoothstep(0.30, 0.42, cell));
            base = mix(base, mesh, max(grille, intake));
            clear *= 1.0 - max(grille, intake) * 0.75;
            base = mix(base, vec3(0.75), emblem); metal = mix(metal, 1.0, emblem);
            if (lamp > 0.0) {
                vec2 d = fz - lampC;
                float bulbs, strip;
                if (uStyle < 0.5) {
                    bulbs = inside(length(d) - 0.06, 0.01); strip = 0.0;
                } else if (uStyle < 3.5) {
                    bulbs = max(inside(length(d - vec2(-0.055, 0.0)) - 0.042, 0.008), inside(length(d - vec2(0.065, 0.005)) - 0.032, 0.008));
                    strip = inside(rbox(d - vec2(0.02, -0.052), vec2(0.10, 0.009), 0.008), 0.006);
                } else {
                    bulbs = inside(rbox(d - vec2(-0.04, 0.0), vec2(0.08, 0.018), 0.015), 0.008);
                    strip = inside(rbox(d - vec2(0.0, 0.03), vec2(0.15, 0.007), 0.006), 0.005);
                }
                vec3 housing = mix(vec3(0.05, 0.055, 0.06), vec3(0.32, 0.34, 0.37), bulbs);
                base = mix(base, housing, lamp);
                metal = mix(metal, 0.9, lamp); clear = mix(clear, 1.3, lamp); shine = mix(shine, 120.0, lamp);
                vec3 turn = vec3(0.6, 0.25, 0.0) * strip * (uStyle < 3.5 ? 1.0 : 0.0);
                base = mix(base, turn, strip * (uStyle < 3.5 ? 0.8 : 0.0));
                vec3 white = vec3(1.0, 0.99, 0.96);
                emit += lamp * (uLights * white * (bulbs * 7.0 + 0.15) + (uStyle > 3.5 ? white * strip * 2.2 : vec3(0.0)));
            }
        }
        // Rear: tri-bar tail lamps.
        float rear = smoothstep(0.15, 0.35, -on.x) * step(vObj.x, rx + 0.45);
        if (rear > 0.0) {
            float y0 = uStyle < 2.5 ? 0.66 : 0.80;
            float housing = inside(rbox(fz - vec2(0.62, y0), vec2(0.21, 0.085), 0.04), aa) * rear;
            float bars = 0.0;
            for (int k = 0; k < 3; k++) {
                float zc = 0.48 + float(k) * 0.135;
                bars = max(bars, inside(rbox(fz - vec2(zc, y0), vec2(0.046, 0.068), 0.02), aa));
            }
            bars *= housing;
            base = mix(base, vec3(0.012), housing);
            base = mix(base, vec3(0.30, 0.004, 0.006), bars);
            clear = mix(clear, 1.3, housing);
            emit += bars * vec3(1.0, 0.04, 0.02) * (0.25 + uLights * 4.0);
            float plate = inside(rbox(fz - vec2(0.0, 0.60), vec2(0.17, 0.07), 0.02), aa) * rear;
            base = mix(base, vec3(0.015), plate * 0.85);
            float diffuser = (1.0 - smoothstep(bottom + 0.10, bottom + 0.13, vObj.y)) * rear;
            base = mix(base, vec3(0.008), diffuser); clear *= 1.0 - diffuser * 0.8;
        }
        // Side markers.
        float sideMark = sideFace * step(0.3, abs(on.z));
        float amber = inside(rbox(vec2(vObj.x - (fx - 0.30), vObj.y - 0.55), vec2(0.055, 0.016), 0.012), aa) * sideMark;
        float red = inside(rbox(vec2(vObj.x - (rx + 0.25), vObj.y - 0.60), vec2(0.05, 0.016), 0.012), aa) * sideMark;
        base = mix(base, vec3(0.45, 0.16, 0.0), amber); base = mix(base, vec3(0.30, 0.0, 0.01), red);
        emit += amber * vec3(1.0, 0.45, 0.0) * uLights * 1.5 + red * vec3(1.0, 0.02, 0.0) * uLights * 1.5;
        // Front lower lip and side sills read darker, as on the real car.
        float lip = (1.0 - smoothstep(bottom + 0.02, bottom + 0.06, vObj.y)) * front;
        base = mix(base, vec3(0.01), lip);
    } else if (m < 1.5) {             // glass
        base = vec3(0.004, 0.006, 0.008); clear = 1.0; shine = 200.0; specK = 1.0;
    } else if (m < 2.5) {             // satin black trim
        base = vec3(0.012); clear = 0.25; shine = 30.0; specK = 0.2;
    } else if (m < 3.5) {             // chrome
        base = vec3(0.02); metal = 0.95; clear = 0.4; shine = 150.0; specK = 1.0;
    } else if (m < 4.5) {             // tyre rubber with a soft sidewall sheen
        base = vec3(0.016); clear = 0.06; shine = 12.0; specK = 0.08;
    } else if (m < 5.5) {             // machined alloy
        base = vec3(0.42, 0.43, 0.45); metal = 0.55; clear = 0.5; shine = 70.0; specK = 0.6;
    } else if (m < 6.5) {             // brake disc
        base = vec3(0.10, 0.10, 0.11); metal = 0.35; shine = 30.0; specK = 0.3;
    } else if (m < 7.5) {             // caliper
        clear = 0.6; shine = 50.0; specK = 0.4;
    } else if (m < 8.5) {             // convertible fabric top
        base = vec3(0.014, 0.015, 0.017); clear = 0.0; shine = 8.0; specK = 0.05;
        base *= 0.85 + 0.15 * smoothstep(0.2, 0.5, abs(fract(vObj.x * 4.2) - 0.5));
    } else if (m < 9.5) {             // interior leather
        base = vec3(0.022, 0.022, 0.024); clear = 0.08; shine = 16.0; specK = 0.08;
    } else {                          // underbody and wheel wells
        base = vec3(0.004); clear = 0.0; specK = 0.0;
    }
    vec3 L1 = normalize(vec3(0.30, 0.90, 0.32));
    vec3 L2 = normalize(vec3(-0.55, 0.45, -0.70));
    float d1 = max(dot(N, L1), 0.0), d2 = max(dot(N, L2), 0.0);
    vec3 ambient = mix(vec3(0.020, 0.022, 0.026), vec3(0.20, 0.22, 0.26), N.y * 0.5 + 0.5);
    float ao = 0.45 + 0.55 * smoothstep(0.0, 0.55, vObj.y);
    if (m > 3.5 && m < 7.5) ao = 1.0;
    vec3 col = base * (ambient * ao + d1 * vec3(1.0, 0.98, 0.95) * 0.9 + d2 * vec3(0.55, 0.62, 0.75) * 0.35);
    vec3 R = reflect(-V, N);
    float NdV = max(dot(N, V), 0.0);
    float F = 0.04 + 0.96 * pow(1.0 - NdV, 5.0);
    vec3 H1 = normalize(L1 + V);
    col += base * env(R) * metal;
    col = mix(col, env(R) * ao, clamp(F * clear, 0.0, 1.0));
    col += vec3(1.0) * pow(max(dot(N, H1), 0.0), shine) * specK * ao;
    col += vec3(1.0) * pow(max(dot(N, H1), 0.0), shine * 6.0) * clear * 0.8 * ao;
    col += emit;
    float alpha = uAlpha;
    if (m > 0.5 && m < 1.5) alpha = mix(0.82, 1.0, F);
    vec3 outc = tonemap(col);
    if (uMirror > 0.5) {
        float fade = 0.30 * exp(-max(-vWorld.y, 0.0) * 2.4);
        gl_FragColor = vec4(outc * fade * alpha, fade * alpha);
    } else {
        gl_FragColor = vec4(outc * alpha, alpha);
    }
}
"""

    const val FLOOR_VERTEX = """
attribute vec3 aPos;
uniform mat4 uViewProj;
varying vec3 vWorld;
void main() { vWorld = aPos; gl_Position = uViewProj * vec4(aPos, 1.0); }
"""

    const val FLOOR_FRAGMENT = COMMON + """
uniform vec4 uCar;      // centreX, halfLength, halfWidth, axle
uniform vec2 uTrack;    // track, frontX
uniform float uLights;
uniform float uTravel;
varying vec3 vWorld;
void main() {
    vec2 p = vWorld.xz;
    float r = length(vec2((p.x - uCar.x) * 0.42, p.y * 0.75));
    float floorA = (1.0 - smoothstep(0.6, 3.2, r)) * 0.92;
    float d = rbox(vec2(p.x - uCar.x, p.y), vec2(uCar.y - 0.25, uCar.z - 0.12), 0.35);
    float shadow = (1.0 - smoothstep(-0.55, 0.85, d)) * 0.80 + (1.0 - smoothstep(-0.20, 0.12, d)) * 0.18;
    for (int i = 0; i < 4; i++) {
        float sx = i < 2 ? uCar.w : -uCar.w;
        float sz = mod(float(i), 2.0) < 0.5 ? uTrack.x : -uTrack.x;
        vec2 q = vec2(p.x - sx, p.y - sz);
        shadow += (1.0 - smoothstep(0.02, 0.38, length(q * vec2(0.85, 1.6)))) * 0.45;
    }
    shadow = clamp(shadow, 0.0, 1.0);
    // Subtle lines advance only with a valid speed.
    float lane = 1.0 - smoothstep(0.004, 0.012, abs(fract((p.x + uTravel) * 0.5) - 0.5) - 0.48);
    vec3 col = vec3(0.020, 0.026, 0.036) + vec3(0.025, 0.04, 0.06) * lane * 0.5;
    // Headlamp pools on the floor ahead of the car.
    vec2 ahead = vec2(p.x - (uTrack.y + 1.9), p.y);
    float px = ahead.x / 1.5; float pz = abs(ahead.y) - 0.55;
    float pool = exp(-px * px - pz * pz / 0.32);
    col += vec3(0.55, 0.62, 0.72) * pool * uLights * 0.55;
    col *= 1.0 - shadow * 0.92;
    float a = clamp(floorA + shadow * 0.75 + pool * uLights * 0.5, 0.0, 1.0);
    gl_FragColor = vec4(tonemap(col) * a, a);
}
"""

    const val GLOW_VERTEX = """
attribute vec2 aCorner;
uniform mat4 uViewProj;
uniform vec3 uCenter;
uniform vec3 uRight;
uniform vec3 uUp;
uniform float uSize;
varying vec2 vUv;
void main() {
    vUv = aCorner;
    vec3 w = uCenter + (uRight * aCorner.x + uUp * aCorner.y) * uSize;
    gl_Position = uViewProj * vec4(w, 1.0);
}
"""

    const val GLOW_FRAGMENT = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform vec3 uColor;
varying vec2 vUv;
void main() {
    float r2 = dot(vUv, vUv);
    float g = exp(-r2 * 9.0) * 0.9 + exp(-r2 * 40.0) * 0.8;
    gl_FragColor = vec4(uColor * g, 0.0);
}
"""
}
