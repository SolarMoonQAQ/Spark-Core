#version 150

#define MINECRAFT_LIGHT_POWER   (0.6)
#define MINECRAFT_LIGHT_POWER_FIXED   (0.5)
#define MINECRAFT_AMBIENT_LIGHT (0.4)
#define MINECRAFT_AMBIENT_LIGHT_FIXED (0.5)

vec4 minecraft_mix_light(vec3 lightDir0, vec3 lightDir1, vec3 normal, vec4 color) {
    lightDir0 = normalize(lightDir0);
    lightDir1 = normalize(lightDir1);
    float light0 = max(0.0, dot(lightDir0, normal));
    float light1 = max(0.0, dot(lightDir1, normal));

    float dotP = dot(lightDir0, lightDir1);
    bool isFixed = dotP > 0.20 && dotP < 0.205;
    float lightPow = isFixed ? MINECRAFT_LIGHT_POWER_FIXED : MINECRAFT_LIGHT_POWER;
    float ambientLight = isFixed ? MINECRAFT_AMBIENT_LIGHT_FIXED : MINECRAFT_AMBIENT_LIGHT;

    float lightAccum = min(1.0, (light0 + light1) * lightPow + ambientLight);
    return vec4(color.rgb * lightAccum, color.a);
}

vec4 minecraft_sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texture(lightMap, clamp(uv / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}