#version 150

uniform sampler2D Sampler0;
uniform float Time;
uniform float Strength;
uniform vec3  WarpCenter;

in vec3 localPos;
out vec4 fragColor;

void main() {
    // 到波心的向量和距离
    vec3 dir3D = localPos - WarpCenter;
    float dist = length(dir3D);

    // 波数和速度
    float waveNumber = 6.0;
    float waveSpeed  = 2.0;

    // 球形波
    float wave = sin(dist * waveNumber - Time * waveSpeed);

    // 振幅衰减
    float amp = 0.003 * Strength / (1.0 + dist);

    // 径向方向（投影到屏幕 UV 空间）
    vec2 dir2D = normalize(dir3D.xy);

    // 只沿径向偏移
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(Sampler0, 0));
    uv += dir2D * wave * amp;

    // 手动计算梯度，避免 mipmap 过早降级
    vec2 texSize = vec2(textureSize(Sampler0, 0));
    vec2 dx = dFdx(uv) * texSize;
    vec2 dy = dFdy(uv) * texSize;

    fragColor = textureGrad(Sampler0, uv, dx, dy);
}