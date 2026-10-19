#version 150 core

// Minecraft 1.21.1 post program. Vanilla blit.vsh supplies texCoord;
// ProjMat and OutSize are also consumed by that vertex shader.
uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform vec2 OutSize;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

// Pure #FF00FF is an application-owned marker, NOT scene temperature.
// Test chroma, never brightness. The balance test rejects red/blue hues
// with just a trace of the other channel. 0.018 keeps faint blended edges.
// An edge mixed so far into its background that it loses magenta chroma
// cannot be recovered from this RGB buffer; keep an unblended outline core.
vec2 marker(vec3 rgb) {
    float redExcess = rgb.r - rgb.g;
    float blueExcess = rgb.b - rgb.g;
    float excess = min(redExcess, blueExcess);
    float balance = excess / max(max(redExcess, blueExcess), 0.00001);
    float hit = step(0.018, excess) * step(0.40, balance);
    float strength = smoothstep(0.018, 0.75, excess) * smoothstep(0.40, 0.95, balance);
    return vec2(hit, strength);
}

// Cold half of ironbow: black -> navy -> violet -> muted red.
// Scene luminance NEVER reaches the separate yellow/white palette.
vec3 coldIronbow(float luminance) {
    float t = 0.10 + 0.48 * pow(clamp(luminance, 0.0, 1.0), 0.60);
    vec3 black = vec3(0.005, 0.006, 0.014);
    vec3 navy = vec3(0.025, 0.045, 0.22);
    vec3 violet = vec3(0.28, 0.025, 0.35);
    vec3 red = vec3(0.58, 0.045, 0.15);
    if (t < 0.22) return mix(black, navy, t / 0.22);
    if (t < 0.44) return mix(navy, violet, (t - 0.22) / 0.22);
    return mix(violet, red, (t - 0.44) / 0.16);
}

// Every accepted marker, including a weak edge, starts at orange.
// The original magenta core reaches ivory white; strong edges go yellow.
vec3 hotIronbow(float strength) {
    vec3 orange = vec3(1.0, 0.28, 0.025);
    vec3 yellow = vec3(1.0, 0.78, 0.09);
    vec3 white = vec3(1.0, 0.98, 0.86);
    if (strength < 0.55) return mix(orange, yellow, strength / 0.55);
    return mix(yellow, white, (strength - 0.55) / 0.45);
}

vec3 sourceAt(ivec2 pixel) {
    // Exact texels prevent filtering from turning a neighboring non-marker
    // into a white core. Clamp at image edges: no wraparound outline ghosts.
    ivec2 last = max(ivec2(InSize), ivec2(1)) - ivec2(1);
    return texelFetch(DiffuseSampler, clamp(pixel, ivec2(0), last), 0).rgb;
}

float heatAt(ivec2 pixel) {
    vec2 m = marker(sourceAt(pixel));
    return m.x * (0.25 + 0.75 * m.y);
}

float grain(ivec2 pixel) {
    // Integer hash is mirrored exactly by preview/thermal_sim.py.
    uint frame = uint(floor(max(Time, 0.0) * 1200.0));
    uint h = uint(pixel.x) * 1973u + uint(pixel.y) * 9277u + frame * 26699u + 911u;
    h = (h ^ (h >> 13u)) * 1274126177u;
    h = h ^ (h >> 16u);
    return float(h & 65535u) / 65535.0 - 0.5;
}

void main() {
    ivec2 pixel = ivec2(texCoord * InSize);
    vec3 src = sourceAt(pixel);
    vec2 own = marker(src);
    float luma = dot(src, vec3(0.2126, 0.7152, 0.0722));
    vec3 color = coldIronbow(luma);

    // 13 fetches total: center + eight 1px neighbors + four 2px neighbors.
    // Dilate the HEAT MASK, never scene luminance. A bright lamp cannot glow.
    float nearHeat = 0.0;
    nearHeat = max(nearHeat, heatAt(pixel + ivec2( 1,  0)));
    nearHeat = max(nearHeat, heatAt(pixel + ivec2(-1,  0)));
    nearHeat = max(nearHeat, heatAt(pixel + ivec2( 0,  1)));
    nearHeat = max(nearHeat, heatAt(pixel + ivec2( 0, -1)));
    nearHeat = max(nearHeat, heatAt(pixel + ivec2( 1,  1)));
    nearHeat = max(nearHeat, heatAt(pixel + ivec2(-1,  1)));
    nearHeat = max(nearHeat, heatAt(pixel + ivec2( 1, -1)));
    nearHeat = max(nearHeat, heatAt(pixel + ivec2(-1, -1)));
    float farHeat = 0.0;
    farHeat = max(farHeat, heatAt(pixel + ivec2( 2,  0)));
    farHeat = max(farHeat, heatAt(pixel + ivec2(-2,  0)));
    farHeat = max(farHeat, heatAt(pixel + ivec2( 0,  2)));
    farHeat = max(farHeat, heatAt(pixel + ivec2( 0, -2)));
    float halo = max(0.72 * nearHeat, 0.32 * farHeat);
    // Non-marker neighbors may become dim orange, NEVER yellow or white.
    color = mix(color, vec3(0.78, 0.22, 0.025), halo);
    if (own.x > 0.5) color = hotIronbow(own.y);

    vec2 uv = gl_FragCoord.xy / max(OutSize, vec2(1.0));
    vec2 centered = uv * 2.0 - 1.0;
    float vignette = 1.0 - 0.08 * smoothstep(0.20, 1.65, dot(centered, centered));
    float scanline = ((int(gl_FragCoord.y) & 1) == 0) ? 0.99 : 1.0;
    color = color * vignette * scanline + vec3(grain(pixel) * 0.008);

    // Hard separation AFTER all decorations, including noise and halo.
    // Non-markers have G <= .48 and B <= .45, so white-hot is impossible.
    // Accepted markers keep an orange minimum even in the darkest corners.
    if (own.x < 0.5) color = min(color, vec3(0.82, 0.48, 0.45));
    else color = max(color, vec3(0.90, 0.24, 0.015));
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
