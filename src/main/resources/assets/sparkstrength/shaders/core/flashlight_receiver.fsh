#version 150

#moj_import <fog.glsl>
#moj_import <light.glsl>
#moj_import <sparkstrength:flashlight.glsl>

uniform sampler2D Sampler0;

uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
// 1 for the translucent-layer pass, 0 for solid and cutout. / 半透明层通道为 1，实心与镂空层为 0。
uniform float PremultiplyAlpha;

in vec3 worldPos;
in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 vertexNormal;

out vec4 fragColor;

// Additive (ONE, ONE) light for terrain that wathe's true darkness leaves pure black: albedo x light, faded by the
// same fog the terrain got. Opaque receivers leave the framebuffer alpha untouched.
// 叠加（ONE, ONE）到被 wathe 真黑暗渲染为纯黑的地形上：反照率 × 光照，并按地形所受的同一雾效衰减。不透明受光面不改动帧缓冲 alpha。
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

    // Lambert-like response with a 0.3 floor for lit faces, faded to 0 as a face turns edge-on so back faces (the far
    // side of a closed door) get nothing and grazing faces do not pop.
    // 受光面采用带 0.3 下限的类 Lambert 响应，并在表面转为侧向时渐变到 0：背面（关闭的门的背光面）不受光，掠射面不会突变。
    vec3 normal = normalize(vertexNormal);
    float facing = max(dot(normal, lightDir), 0.0);
    intensity *= mix(0.3, 1.0, facing) * smoothstep(0.0, 0.1, facing);

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
    // Translucent layer: premultiply by alpha, i.e. light only the share of the pixel the glass covers; what shows
    // through it already got its own light. / 半透明层：按 alpha 预乘，只照亮玻璃覆盖的像素份额；透过玻璃可见的内容已各自受光。
    float coverage = mix(1.0, albedo.a, PremultiplyAlpha);
    if (coverage <= 0.0) {
        discard;
    }
    float light = flashlight_exposed(intensity) * keep * coverage;
    // Translucent pass: add 1/255 alpha. Fabulous composites its translucent target premultiplied but skips texels
    // whose alpha is exactly 0, and Sodium's render-pass optimisation draws translucent-layer quads with opaque
    // textures (wathe's hull, the frosted privacy panel) into the main target, leaving that texel empty; the mark lets
    // the light through at a 0.4% dimming. The main framebuffer's alpha is never displayed.
    // 半透明通道：alpha 增加 1/255。“极佳”画质按预乘方式合成半透明目标，但会跳过 alpha 恰为 0 的纹素；Sodium 的渲染通道
    // 优化会把纹理不透明的半透明层四边形（wathe 船体、雾化隐私面板）画进主目标，使该纹素为空；此标记让光照得以合成，
    // 仅变暗 0.4%。主帧缓冲的 alpha 不会显示。
    fragColor = vec4(albedo.rgb * vertexColor.rgb * LightColor * light, PremultiplyAlpha / 255.0);
}
