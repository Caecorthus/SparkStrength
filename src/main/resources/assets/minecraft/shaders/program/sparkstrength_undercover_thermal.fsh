#version 150

// SparkStrength Undercover thermal, after Tarkov's T-7 goggles: a clean, full-resolution white-hot monochrome image.
// DiffuseSampler = the rendered frame; HeatSampler = living bodies (and the Undercover's own arm) re-rendered flat white
// (alpha 1) over the world depth, so only the parts the camera really sees are hot.
// SparkStrength 卧底热成像，参照塔科夫 T-7：干净的全分辨率白热单色画面。
// DiffuseSampler 是渲染好的画面；HeatSampler 是以世界深度重新渲染的纯白活人（以及卧底自己的手臂，alpha 1），
// 只有相机真正看得到的部分才是热的。

uniform sampler2D DiffuseSampler;
uniform sampler2D HeatSampler;
uniform vec2 OutSize;

in vec2 texCoord;

out vec4 fragColor;

float heatAt(vec2 uv) {
    return texture(HeatSampler, uv).a;
}

float lumAt(vec2 uv) {
    return dot(texture(DiffuseSampler, uv).rgb, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 texel = 1.0 / OutSize;
    // Bloom radius in pixels, scaled with the screen height so it looks the same at any resolution.
    // 柔光半径（像素），随屏幕高度缩放，任何分辨率下看起来都一样。
    float unit = max(1.0, OutSize.y / 360.0);

    // The world in readable greys: a slight softening (four linear taps), shadows lifted, lamps a little warmer.
    // 世界是看得清的灰色：轻微柔化（四次线性采样），暗部提亮，灯光稍暖。
    float lum = 0.25 * (lumAt(texCoord + texel * vec2(-0.5, -0.5)) + lumAt(texCoord + texel * vec2(0.5, -0.5))
            + lumAt(texCoord + texel * vec2(-0.5, 0.5)) + lumAt(texCoord + texel * vec2(0.5, 0.5)));
    float world = 0.12 + 0.50 * pow(lum, 0.7) + 0.10 * smoothstep(0.80, 1.0, lum);

    // Body heat: a solid core plus a bloom from two rings around the pixel. Bodies aren't flat: lit skin reads
    // hottest and darker clothes a touch cooler, so faces and facing still show.
    // 体温：实心核心，加上周围两圈的柔光。人体不是一块平白：亮的皮肤最热，深色衣服稍凉，脸和朝向依然看得出。
    float core = heatAt(texCoord);
    float near = 0.0;
    float far = 0.0;
    for (int i = 0; i < 8; i++) {
        float angle = float(i) * 0.785398;
        vec2 dir = vec2(cos(angle), sin(angle)) * texel * unit;
        near += heatAt(texCoord + dir * 2.0);
        if (i % 2 == 0) {
            far += heatAt(texCoord + dir * 5.0);
        }
    }
    float bloom = near / 8.0 * 0.7 + far / 4.0 * 0.3;
    float body = 0.82 + 0.16 * smoothstep(0.05, 0.60, lum);
    float value = mix(world, body, core);
    value = max(value, world + bloom * 0.35);

    // Eyepiece: a rounded window with a soft dark rim and a light falloff toward the edges.
    // 目镜：圆角视窗加柔和的暗边，边缘略暗。
    vec2 p = (texCoord - 0.5) * 2.0;
    vec2 edge = abs(p) - vec2(0.82, 0.76);
    float corner = length(max(edge, 0.0)) + min(max(edge.x, edge.y), 0.0);
    float window = 1.0 - smoothstep(0.10, 0.20, corner);
    value *= window * (1.0 - 0.06 * dot(p, p));

    fragColor = vec4(vec3(clamp(value, 0.0, 1.0)), 1.0);
}
