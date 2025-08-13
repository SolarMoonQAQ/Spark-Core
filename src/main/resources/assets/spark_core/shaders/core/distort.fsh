#version 150

uniform sampler2D Sampler0;

uniform vec2 ScreenSize;   // 像素
uniform vec2 Center;       // 像素（左上为 0,0）
uniform float Radius;      // 像素
uniform float BandWidth;   // 像素
uniform float Strength;    // 0~1
uniform float Time;

out vec4 fragColor;

void main() {
    // 把 gl_FragCoord（左下为 0,0）翻成左上为 0,0，统一到 Center 的坐标系
    vec2 fragPx = vec2(gl_FragCoord.x, ScreenSize.y - gl_FragCoord.y);

    float dist = distance(fragPx, Center);

    // 稳健的窄带包络（避免 BandWidth 接近 0 时掉成全 0）
    float bw = max(BandWidth, 0.5);
    float edgeIn  = smoothstep(-bw, 0.0, dist - Radius);
    float edgeOut = 1.0 - smoothstep(0.0, bw, dist - Radius);
    float edge = edgeIn * edgeOut;

    float localStrength = Strength * edge;

    // 轻微波动让波前更“活”
    float wave = sin(dist * 0.05 - Time * 8.0) * 0.5 + 0.5;
    localStrength *= mix(0.96, 1.04, wave);

    // 从中心向外的偏移
    vec2 dir = normalize(fragPx - Center + vec2(1e-5));

    // 位移像素数：可按口味调大一点更明显
    vec2 offsetPx = dir * localStrength * 10.0;

    // 把像素坐标转回 UV 采样
    vec2 uv = (fragPx - offsetPx) / ScreenSize;

    fragColor = texture(Sampler0, uv);
}
