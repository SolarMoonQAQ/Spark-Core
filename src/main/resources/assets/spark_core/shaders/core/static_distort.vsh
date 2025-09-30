#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out vec2 texCoord;  // 传给 fsh 的屏幕 UV
out vec4 vertexColor;

void main() {
    // MVP 变换
    vec4 clipPos = ProjMat * ModelViewMat * vec4(Position, 1.0);
    gl_Position = clipPos;

    // 将裁剪空间坐标转换到 NDC [-1,1]，再映射到 [0,1]
    vec2 ndc = clipPos.xy / clipPos.w;
    texCoord = ndc * 0.5 + 0.5;

    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color) * texelFetch(Sampler2, UV2 / 16, 0);
}
