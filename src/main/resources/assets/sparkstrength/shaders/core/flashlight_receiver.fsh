#version 150

#moj_import <fog.glsl>
#moj_import <light.glsl>
#moj_import <sparkstrength:flashlight.glsl>

uniform sampler2D Sampler0;

uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec3 worldPos;
in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 vertexNormal;

out vec4 fragColor;

// Additive (ONE, ONE) light for terrain that wathe's true darkness leaves pure black: albedo x light, faded by the
// same fog the terrain got. Alpha stays 0 so the framebuffer alpha is untouched.
// 叠加（ONE, ONE）到被 wathe 真黑暗渲染为纯黑的地形上：反照率 × 光照，并按地形所受的同一雾效衰减。
// 输出 alpha 为 0，不改动帧缓冲 alpha。
void main() {
    vec4 albedo = texture(Sampler0, texCoord0);
    // Vertex alpha carries the block layer's cutout threshold (0 for solid). / 顶点 alpha 携带方块层的镂空阈值（实心层为 0）。
    if (albedo.a < vertexColor.a) {
        discard;
    }

    vec3 toLight = LightOrigin - worldPos;
    float dist = max(length(toLight), 1.0e-4);
    vec3 lightDir = toLight / dist;
    float intensity = flashlight_intensity(dot(-lightDir, LightDirection), dist);
    if (intensity <= 0.0) {
        discard;
    }

    vec3 normal = normalize(vertexNormal);
    float facing = max(dot(normal, lightDir), 0.0);
    intensity *= mix(0.3, 1.0, facing);

    // Normal offset: lift the lookup by about one grid cell at grazing angles, so a floor seen at a shallow angle does
    // not shadow itself where one cell's hit distance spans several blocks. The isShadowed test itself is unchanged.
    // 法线偏移：掠射角下把查询点抬高约一个网格单元，避免浅角度地面因单元命中距离跨越数格而自阴影；isShadowed 判定本身不变。
    float cellSize = dist * 2.0 * RayGrid.x / RayGrid.y;
    vec2 texel;
    float shadowDist;
    bool inGrid = flashlight_grid_point(worldPos + normal * (cellSize * (1.0 - facing)), texel, shadowDist);
    if (ShadowEnabled > 0.5) {
        intensity *= inGrid ? flashlight_visibility(texel, shadowDist) : 0.0;
    }
    if (inGrid) {
        intensity *= flashlight_ambient_scale(texel);
    }
    if (intensity <= 0.0) {
        discard;
    }

    float keep = flashlight_fog_keep(vertexDistance, FogStart, FogEnd, FogColor);
    fragColor = vec4(albedo.rgb * vertexColor.rgb * LightColor * (flashlight_exposed(intensity) * keep), 0.0);
}
