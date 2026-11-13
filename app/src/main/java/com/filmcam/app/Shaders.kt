package com.filmcam.app

object Shaders {

    const val VERTEX = """
attribute vec2 aPos;
uniform mat4 uTexMatrix;
varying vec2 vTex;
varying vec2 vUv;
void main() {
    vUv = aPos * 0.5 + 0.5;
    vTex = (uTexMatrix * vec4(vUv, 0.0, 1.0)).xy;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

    const val FRAGMENT = """
#extension GL_OES_EGL_image_external : require
precision highp float;

varying vec2 vTex;
varying vec2 vUv;

uniform samplerExternalOES uTex;
uniform vec2 uTexel;
uniform float uExposure;
uniform float uContrast;
uniform float uSaturation;
uniform float uTemp;
uniform float uTint;
uniform float uFade;
uniform float uGlow;
uniform float uGrain;
uniform float uVignette;
uniform float uMono;
uniform float uSplit;
uniform float uIntensity;
uniform float uTime;
uniform vec3 uShadowTint;
uniform vec3 uHighlightTint;

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 grade(vec3 c) {
    // 밝기
    c *= exp2(uExposure * 0.9);
    // 온도(따뜻/차갑) + 틴트(핑크/그린)
    c *= vec3(
        1.0 + uTemp * 0.20 + uTint * 0.04,
        1.0 - uTint * 0.12,
        1.0 - uTemp * 0.20 + uTint * 0.04
    );
    // 대비
    c = (c - 0.5) * (1.0 + uContrast * 0.8) + 0.5;
    c = max(c, 0.0);
    // 채도
    float l = luma(c);
    c = mix(vec3(l), c, 1.0 + uSaturation);
    c = max(c, 0.0);
    // 흑백
    c = mix(c, vec3(luma(c)), uMono);
    // 페이드: 블랙을 들어올리고 화이트를 살짝 눌러줌
    c = c * (1.0 - uFade * 0.25) + uFade * 0.12;
    // 그림자 / 하이라이트에 색 입히기 (스플릿 토닝)
    float lm = luma(c);
    float sw = 1.0 - smoothstep(0.0, 0.65, lm);
    float hw = smoothstep(0.35, 1.0, lm);
    c = mix(c, c * uShadowTint * 2.0, sw * uSplit);
    c = mix(c, c * uHighlightTint * 2.0, hw * uSplit);
    return clamp(c, 0.0, 1.0);
}

void main() {
    vec3 raw = texture2D(uTex, vTex).rgb;
    vec3 c = grade(raw);

    // 글로우: 주변을 흐려서 섞어 몽글몽글한 빛번짐
    if (uGlow > 0.001) {
        vec3 blur = vec3(0.0);
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.785398;
            vec2 d = vec2(cos(a), sin(a));
            blur += texture2D(uTex, vTex + d * uTexel * 4.0).rgb;
            blur += texture2D(uTex, vTex + d * uTexel * 10.0).rgb;
        }
        blur = grade(blur / 16.0);
        vec3 soft = mix(c, blur, uGlow * 0.45);
        vec3 scr = 1.0 - (1.0 - soft) * (1.0 - blur * 0.6);
        c = mix(soft, scr, uGlow * 0.5);
    }

    // 필터 강도 (0이면 원본)
    c = mix(raw, c, uIntensity);

    // 비네팅
    vec2 p = (vUv - 0.5) * 2.0;
    float vig = smoothstep(0.35, 1.25, length(p));
    c *= 1.0 - uVignette * uIntensity * vig * 0.75;

    // 필름 그레인 (중간톤에서 더 강하게)
    float n = hash(gl_FragCoord.xy + vec2(uTime * 37.0, uTime * 17.0)) - 0.5;
    float lg = luma(c);
    c += n * uGrain * uIntensity * 0.22 * (1.0 - abs(lg * 2.0 - 1.0) * 0.6);

    gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
}
