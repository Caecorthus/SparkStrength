#version 150

// Flashlight beam math, mirrored line by line from annina.sparkstrength.role.attendant.FlashlightBeamRules. Every
// number arrives as a uniform built by FlashlightShaderConstants, so the values live only in Java. Import <fog.glsl>
// and <light.glsl> before this file (it calls linear_fog and minecraft_sample_lightmap).
// 手电筒光束数学，逐行镜像 FlashlightBeamRules。所有数值都由 FlashlightShaderConstants 以 uniform 传入，
// 只在 Java 中定义。导入本文件前须先导入 <fog.glsl> 与 <light.glsl>（本文件调用 linear_fog 与 minecraft_sample_lightmap）。

uniform vec3 ConeCos;        // cosInner, cosOuter, cosSpill
uniform float SpillStrength;
uniform vec2 RangeFalloff;   // RANGE_BLOCKS, FALLOFF_BLOCKS
uniform float PeakIntensity;
uniform float Exposure;
uniform vec2 ShadowBias;     // SHADOW_BIAS_BLOCKS, SHADOW_BIAS_PER_BLOCK
uniform vec3 RayGrid;        // rayGridTangentExtent(), RAY_GRID_SIZE, decodeDistance(1)

// Per light, camera-relative. ShadowOrigin/Forward/Right/Up are the ray map basis at cast (tick) time, which can lag
// the frame-interpolated LightOrigin/LightDirection; shadows use the map basis so they stay fixed in the world.
// 每个光源，相对相机。Shadow* 为射线图投射（tick）时的基向量，可能落后于逐帧插值的 LightOrigin/LightDirection；
// 阴影使用射线图基向量，因此在世界中保持稳定。
uniform vec3 LightOrigin;
uniform vec3 LightDirection;
uniform vec3 LightColor;
uniform vec3 ShadowOrigin;
uniform vec3 ShadowForward;
uniform vec3 ShadowRight;
uniform vec3 ShadowUp;
uniform float ShadowEnabled;   // 0 for camera-anchored lights: the depth test already is exact occlusion.
uniform float AmbientEnabled;  // 0 when there is no ray map yet.

uniform sampler2D Sampler2;
uniform sampler2D RayMapSampler;

float flashlight_cone(float cosAngle) {
    float core = smoothstep(ConeCos.y, ConeCos.x, cosAngle);
    float halo = SpillStrength * smoothstep(ConeCos.z, ConeCos.y, cosAngle);
    return core + (1.0 - core) * halo;
}

float flashlight_distance(float dist) {
    if (dist >= RangeFalloff.x) {
        return 0.0;
    }
    float clamped = max(0.0, dist);
    float ratio = clamped / RangeFalloff.x;
    float window = 1.0 - ratio * ratio * ratio * ratio;
    float falloff = clamped / RangeFalloff.y;
    return window * window / (1.0 + falloff * falloff);
}

float flashlight_intensity(float cosAngle, float dist) {
    return PeakIntensity * flashlight_cone(cosAngle) * flashlight_distance(dist);
}

float flashlight_exposed(float intensity) {
    return 1.0 - exp(-Exposure * max(0.0, intensity));
}

float flashlight_tangent_to_grid_unit(float tangent) {
    return (tangent / RayGrid.x + 1.0) * 0.5;
}

float flashlight_shadow_bias(float dist) {
    return ShadowBias.x + ShadowBias.y * max(0.0, dist);
}

// hitDistance is where the cell's ray LEAVES its first occluder (FlashlightRayMap). The occluder's thickness is the
// slack for its own lit faces, so the bias is slightly negative up close (points on a door's far face are shadowed).
// hitDistance 为单元射线离开首个遮挡体的距离（见 FlashlightRayMap）。遮挡体厚度即其受光面的余量，
// 因此近处偏移略为负（门背面上的点处于阴影中）。
bool flashlight_is_shadowed(float dist, float hitDistance) {
    return dist > hitDistance + flashlight_shadow_bias(dist);
}

// Continuous texel position of a camera-relative point in the ray grid and its distance from the cast origin; false
// when the point is behind the light. Texel (u, v) is cell (u, v); v grows along ShadowUp.
// 相对相机的点在射线网格中的连续纹素坐标及其到投射原点的距离；点在光源后方时返回 false。
bool flashlight_grid_point(vec3 pos, out vec2 texel, out float dist) {
    vec3 rel = pos - ShadowOrigin;
    float forward = dot(rel, ShadowForward);
    dist = length(rel);
    if (forward <= 1.0e-4) {
        texel = vec2(-1.0);
        return false;
    }
    float tangentU = dot(rel, ShadowRight) / forward;
    float tangentV = dot(rel, ShadowUp) / forward;
    texel = vec2(flashlight_tangent_to_grid_unit(tangentU), flashlight_tangent_to_grid_unit(tangentV)) * RayGrid.y;
    return true;
}

// 1 when the cell's ray reaches dist, 0 when an occluder lies before it; cells outside the grid are unlit.
// R/G hold the high/low byte of encodeDistance(occluder exit).
// 单元射线能到达 dist 时为 1，之前有遮挡体时为 0；网格外的单元视为不受光。R/G 为遮挡体出口距离编码的高/低字节。
float flashlight_cell_lit(ivec2 cell, float dist) {
    int size = int(RayGrid.y);
    if (cell.x < 0 || cell.y < 0 || cell.x >= size || cell.y >= size) {
        return 0.0;
    }
    vec4 data = texelFetch(RayMapSampler, cell, 0);
    float encoded = floor(data.r * 255.0 + 0.5) * 256.0 + floor(data.g * 255.0 + 0.5);
    return flashlight_is_shadowed(dist, encoded * RayGrid.z) ? 0.0 : 1.0;
}

// 2x2 PCF over nearest texel fetches: a bilinear blend of four exact isShadowed tests.
// 2x2 PCF：对四个精确 isShadowed 测试结果做双线性混合。
float flashlight_visibility(vec2 texel, float dist) {
    vec2 position = texel - 0.5;
    vec2 base = floor(position);
    vec2 blend = position - base;
    ivec2 cell = ivec2(base);
    float lit00 = flashlight_cell_lit(cell, dist);
    float lit10 = flashlight_cell_lit(cell + ivec2(1, 0), dist);
    float lit01 = flashlight_cell_lit(cell + ivec2(0, 1), dist);
    float lit11 = flashlight_cell_lit(cell + ivec2(1, 1), dist);
    return mix(mix(lit00, lit10, blend.x), mix(lit01, lit11, blend.x), blend.y);
}

// Halves the flashlight where the scene is already lit: B/A hold block/sky light * 17 of the open cell before the
// occluder, looked up through the live lightmap (wathe's true darkness maps level 0 to black, so dark areas keep 1.0).
// 场景本已明亮处将手电亮度减半：B/A 为命中前空气格的方块光/天空光 ×17，经实时光照贴图查询
// （wathe 真黑暗把 0 级映射为纯黑，所以黑暗处保持 1.0）。
float flashlight_ambient_scale(vec2 texel) {
    if (AmbientEnabled < 0.5) {
        return 1.0;
    }
    int size = int(RayGrid.y);
    ivec2 cell = clamp(ivec2(floor(texel)), ivec2(0), ivec2(size - 1));
    vec4 data = texelFetch(RayMapSampler, cell, 0);
    ivec2 level = ivec2(floor(data.b * 15.0 + 0.5), floor(data.a * 15.0 + 0.5));
    vec3 ambient = minecraft_sample_lightmap(Sampler2, level * 16).rgb;
    return mix(1.0, 0.5, dot(ambient, vec3(0.2126, 0.7152, 0.0722)));
}

// Share of a fragment that survives the scene fog, exactly as vanilla linear_fog blends towards FogColor.
// 片元在场景雾中保留的比例，与原版 linear_fog 混向雾色的方式一致。
float flashlight_fog_keep(float fogDistance, float fogStart, float fogEnd, vec4 fogColor) {
    return linear_fog(vec4(1.0), fogDistance, fogStart, fogEnd, vec4(0.0, 0.0, 0.0, fogColor.a)).r;
}
