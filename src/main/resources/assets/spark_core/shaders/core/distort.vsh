#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

// 把模型局部坐标传给片元着色器
out vec3 localPos;

void main() {
    localPos = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
