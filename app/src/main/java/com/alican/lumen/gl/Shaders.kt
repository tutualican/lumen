package com.alican.lumen.gl

/**
 * GLSL ES 3.00 sources for the shared look pipeline.
 *
 * Pipeline per frame:
 *  1. Grade pass   -> full-res FBO (colour math, chromatic aberration, pixelation, sharpen)
 *  2. Bright pass  -> quarter-res (bloom) and eighth-res (halation) sources
 *  3. Blur passes  -> separable gaussian, two iterations per chain
 *  4. Composite    -> bloom + halation + grain + vignette + leak + letterbox + stamp
 */
internal object Shaders {

    const val VERTEX = """
#version 300 es
layout(location = 0) in vec2 aPos;
out vec2 vUv;
void main() {
    // Top-left origin UV space; the source is sampled accordingly.
    vUv = vec2(aPos.x * 0.5 + 0.5, 0.5 - aPos.y * 0.5);
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

    const val VERTEX_FLIP = """
#version 300 es
layout(location = 0) in vec2 aPos;
out vec2 vUv;
uniform float uFlipY;
void main() {
    vUv = vec2(aPos.x * 0.5 + 0.5, 0.5 - aPos.y * 0.5);
    gl_Position = vec4(aPos.x, aPos.y * uFlipY, 0.0, 1.0);
}
"""

    private const val GRADE_HEAD = """
precision highp float;
in vec2 vUv;
out vec4 fragColor;

#ifdef USE_OES
uniform samplerExternalOES uTex;
uniform mat4 uTexMatrix;
vec4 sampleTex(vec2 uv) {
    return texture(uTex, (uTexMatrix * vec4(uv.x, 1.0 - uv.y, 0.0, 1.0)).xy);
}
#else
uniform sampler2D uTex;
vec4 sampleTex(vec2 uv) {
    return texture(uTex, uv);
}
#endif

uniform vec2 uTexel;
uniform float uExposure;
uniform float uTemperature;
uniform float uTintAmt;
uniform float uContrast;
uniform float uSaturation;
uniform float uVibrance;
uniform float uFade;
uniform float uGamma;
uniform float uHue;
uniform float uShadowAmt;
uniform float uHighAmt;
uniform float uMono;
uniform float uChromatic;
uniform float uPixelSize;
uniform float uSharpen;
uniform vec3 uShadowTint;
uniform vec3 uHighTint;
uniform vec3 uChannelMix;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

vec3 hueRotate(vec3 c, float angle) {
    if (abs(angle) < 0.0001) return c;
    vec3 k = vec3(0.57735026919);
    float ca = cos(angle);
    float sa = sin(angle);
    mat3 m = ca * mat3(1.0) +
             sa * mat3(0.0, -k.z, k.y,
                       k.z, 0.0, -k.x,
                       -k.y, k.x, 0.0) +
             (1.0 - ca) * outerProduct(k, k);
    return clamp(m * c, 0.0, 4.0);
}
"""

    private const val GRADE_BODY = """
void main() {
    vec2 uv = vUv;

    if (uPixelSize > 0.5) {
        vec2 grid = uPixelSize * uTexel;
        uv = (floor(uv / grid) + 0.5) * grid;
    }

    vec3 col;
    if (uChromatic > 0.0001) {
        vec2 d = (uv - 0.5) * uChromatic * 0.0045;
        col.r = sampleTex(uv + d).r;
        col.g = sampleTex(uv).g;
        col.b = sampleTex(uv - d).b;
    } else {
        col = sampleTex(uv).rgb;
    }

    if (uSharpen > 0.0001) {
        vec3 around = sampleTex(uv + vec2(uTexel.x, 0.0)).rgb +
                      sampleTex(uv - vec2(uTexel.x, 0.0)).rgb +
                      sampleTex(uv + vec2(0.0, uTexel.y)).rgb +
                      sampleTex(uv - vec2(0.0, uTexel.y)).rgb;
        vec3 local = around * 0.25;
        col = clamp(col + (col - local) * uSharpen, 0.0, 4.0);
    }

    col = max(col, vec3(0.0));
    col *= exp2(uExposure);

    // White balance (temperature: +warm, tint: +magenta).
    col *= vec3(1.0 + uTemperature * 0.35, 1.0 - uTintAmt * 0.18, 1.0 - uTemperature * 0.35);

    float luma = dot(col, LUMA);
    float shadowMask = 1.0 - smoothstep(0.0, 0.55, luma);
    float highMask = smoothstep(0.45, 1.0, luma);
    col += uShadowTint * shadowMask * uShadowAmt;
    col += uHighTint * highMask * uHighAmt;

    col = hueRotate(col, uHue);

    luma = dot(col, LUMA);
    vec3 saturated = mix(vec3(luma), col, 1.0 + uSaturation);
    float mx = max(saturated.r, max(saturated.g, saturated.b));
    float mn = min(saturated.r, min(saturated.g, saturated.b));
    float satRange = clamp(mx - mn, 0.0, 1.0);
    col = mix(vec3(dot(saturated, LUMA)), saturated, 1.0 + uVibrance * (1.0 - satRange));

    col = (col - 0.5) * (1.0 + uContrast) + 0.5;
    col = clamp(col, 0.0, 4.0);

    col = pow(max(col, 0.0), vec3(1.0 / max(uGamma, 0.05)));

    float lift = uFade * 0.18;
    col = col * (1.0 - lift) + lift;

    col = mix(col, vec3(dot(col, uChannelMix)), clamp(uMono, 0.0, 1.0));

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""

    fun gradeShader(useOes: Boolean): String {
        val sb = StringBuilder()
        sb.append("#version 300 es\n")
        if (useOes) {
            sb.append("#extension GL_OES_EGL_image_external_essl3 : require\n")
            sb.append("#define USE_OES 1\n")
        }
        sb.append(GRADE_HEAD)
        sb.append(GRADE_BODY)
        return sb.toString()
    }

    const val BRIGHT = """
#version 300 es
precision highp float;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uTex;
uniform float uThreshold;
const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
void main() {
    vec3 c = texture(uTex, vUv).rgb;
    float l = dot(c, LUMA);
    float w = smoothstep(uThreshold, uThreshold + 0.35, l);
    fragColor = vec4(c * w, 1.0);
}
"""

    const val BLUR = """
#version 300 es
precision highp float;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uTex;
uniform vec2 uDir;

void main() {
    // 9-tap gaussian, linear-sampled equivalents where possible.
    vec3 sum = texture(uTex, vUv).rgb * 0.2270270270;
    sum += (texture(uTex, vUv + uDir * 1.3846153846).rgb +
            texture(uTex, vUv - uDir * 1.3846153846).rgb) * 0.3162162162;
    sum += (texture(uTex, vUv + uDir * 3.2307692308).rgb +
            texture(uTex, vUv - uDir * 3.2307692308).rgb) * 0.0702702703;
    fragColor = vec4(sum, 1.0);
}
"""

    const val COMPOSITE = """
#version 300 es
precision highp float;
in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uBase;
uniform sampler2D uBloom;
uniform sampler2D uHalation;
uniform sampler2D uStamp;
uniform vec2 uFullRes;
uniform vec2 uUvScale;
uniform vec2 uUvOffset;
uniform int uRotation;
uniform float uFullAspect;
uniform float uBloomStr;
uniform float uHalStr;
uniform float uVignette;
uniform float uGrain;
uniform float uGrainSize;
uniform float uTime;
uniform float uLeak;
uniform float uLetterbox;
uniform float uScanline;
uniform float uStampOn;
uniform vec3 uHalTint;
uniform vec3 uLeakTint;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec2 rotateUv(vec2 uv, int q) {
    if (q == 1) return vec2(uv.y, 1.0 - uv.x);
    if (q == 2) return vec2(1.0 - uv.x, 1.0 - uv.y);
    if (q == 3) return vec2(1.0 - uv.y, uv.x);
    return uv;
}

void main() {
    vec2 srcUv = rotateUv(vUv, uRotation) * uUvScale + uUvOffset;
    vec2 guv = vUv * uUvScale + uUvOffset;

    vec3 col = texture(uBase, srcUv).rgb;
    if (uBloomStr > 0.0001) {
        col += texture(uBloom, srcUv).rgb * uBloomStr;
    }
    if (uHalStr > 0.0001) {
        col += texture(uHalation, srcUv).rgb * uHalTint * uHalStr;
    }

    // Light leak: warm falloff from the top-right corner plus a cooler corner.
    if (uLeak > 0.0001) {
        vec2 d1 = (guv - vec2(1.05, -0.05)) * vec2(uFullAspect, 1.0);
        float r1 = length(d1);
        float leak = exp(-r1 * r1 * 2.2);
        vec2 d2 = (guv - vec2(-0.05, 1.05)) * vec2(uFullAspect, 1.0);
        float r2 = length(d2);
        leak += 0.55 * exp(-r2 * r2 * 3.0);
        col += uLeakTint * leak * uLeak * 0.85;
    }

    // Vignette, aspect corrected.
    if (uVignette > 0.0001) {
        vec2 d = (guv - 0.5) * vec2(uFullAspect, 1.0);
        float r = length(d) / max(length(vec2(uFullAspect * 0.5, 0.5)), 0.0001);
        col *= 1.0 - uVignette * smoothstep(0.42, 1.0, r) * 0.92;
    }

    // Film grain, more pronounced in the mid-tones.
    if (uGrain > 0.0001) {
        vec2 gp = floor(guv * uFullRes / max(uGrainSize, 1.0)) + uTime * vec2(37.0, 17.0);
        float n = hash(gp) - 0.5;
        float l = dot(col, LUMA);
        float amp = uGrain * (0.55 - 0.30 * abs(l - 0.5));
        col += vec3(n) * amp;
    }

    if (uScanline > 0.0001) {
        float s = 0.5 + 0.5 * sin(guv.y * uFullRes.y * 2.4);
        col *= 1.0 - uScanline * 0.55 * s;
    }

    if (uLetterbox > 0.0001) {
        float bar = uLetterbox;
        if (guv.y < bar || guv.y > 1.0 - bar) {
            col = vec3(0.0);
        }
    }

    if (uStampOn > 0.5) {
        vec2 size = vec2(0.34, 0.34 * uFullAspect * 0.3125);
        vec2 origin = vec2(1.0 - size.x - 0.055, 1.0 - size.y - 0.055);
        vec2 st = (guv - origin) / size;
        if (st.x >= 0.0 && st.x <= 1.0 && st.y >= 0.0 && st.y <= 1.0) {
            vec4 s = texture(uStamp, st);
            vec3 stampColor = s.a > 0.001 ? s.rgb / s.a : s.rgb;
            col = mix(col, stampColor, s.a * 0.92);
        }
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""
}
