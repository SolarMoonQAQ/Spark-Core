/*
 * fragment shader for the Debug/LocalNormals program
 */
#version 330 core

in vec3 Normal_modelspace; // vertex normals from the vertex shader
out vec3 fragColor;

void main() {
    vec3 srgb = (Normal_modelspace * vec3(0.5)) + vec3(0.5);
    fragColor = pow(srgb, vec3(2.2, 2.2, 2.2));
}
