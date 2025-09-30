#version 150

uniform sampler2D Sampler0;   // 屏幕颜色贴图
uniform sampler2D Sampler1;   // 扰动贴图

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    // 采样扰动贴图
    vec2 distort = texture(Sampler1, texCoord * 2.0).rg;
    distort = distort * 2.0 - 1.0;

    // 根据扰动强度偏移屏幕坐标
    vec2 refractUV = texCoord + distort * 0.025;
    refractUV = clamp(refractUV, 0.0, 1.0);

    // 从屏幕颜色贴图采样背景
    vec4 sceneColor = texture(Sampler0, refractUV);

    // --- 只取上下边缘 ---
    float edgeY = min(texCoord.y, 1.0 - texCoord.y);
    float edgeMask = 1.0 - smoothstep(0.0, 0.5, edgeY);

    // 在上下边缘叠加白色高光
    vec3 finalColor = sceneColor.rgb + vec3(1.0) * edgeMask * 0.8;

    fragColor = vec4(clamp(finalColor, 0.0, 1.0), sceneColor.a * vertexColor.a);
}
