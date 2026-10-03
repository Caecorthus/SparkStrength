#version 150

#moj_import <fog.glsl>
#moj_import <light.glsl>
#moj_import <sparkstrength:flashlight.glsl>

// DepthSampler is a private copy of the scene depth, so this pass never samples the depth buffer it is bound to.
// DepthSampler 为场景深度的私有副本，因此本通道不会采样自身绑定的深度缓冲。
uniform sampler2D DepthSampler;
uniform mat4 InvViewProjMat;   // clip -> camera-relative world
uniform mat4 ViewProjMat;      // camera-relative world -> clip
uniform vec2 ScreenSize;

uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform int FogShape;

uniform float BeamStrength;
uniform float GlareStrength;

in vec2 texCoord;

out vec4 fragColor;

const int STEPS = 16;
const float FAR = 1.0e6;
const vec2 GLARE_TAPS[5] = vec2[5](vec2(0.0), vec2(2.0, 0.0), vec2(-2.0, 0.0), vec2(0.0, 2.0), vec2(0.0, -2.0));
const float GLARE_RADIUS = 0.3;
const float GLARE_DEPTH_SLACK = 0.3;

vec3 unproject(vec2 uv, float depth) {
    vec4 point = InvViewProjMat * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return point.xyz / point.w;
}

vec2 sphere_interval(vec3 start, vec3 dir, vec3 center, float radius) {
    vec3 offset = start - center;
    float halfB = dot(dir, offset);
    float disc = halfB * halfB - (dot(offset, offset) - radius * radius);
    if (disc < 0.0) {
        return vec2(1.0, 0.0);
    }
    float root = sqrt(disc);
    return vec2(-halfB - root, -halfB + root);
}

// Ray parameter range inside the front nappe of a cone (convex, so one interval). Falls back to the front half-space
// when the ray runs parallel to the cone surface; samples outside the cone still weigh 0 through flashlight_cone.
// 射线位于圆锥前半锥体内的参数区间（凸集，故只有一段）。射线与锥面平行时退回到前半空间；锥外采样点仍会因 flashlight_cone 得到 0 权重。
vec2 cone_interval(vec3 start, vec3 dir, vec3 apex, vec3 axis, float cosHalf) {
    vec3 offset = start - apex;
    float dirAxis = dot(dir, axis);
    float offsetAxis = dot(offset, axis);
    float cos2 = cosHalf * cosHalf;
    vec2 front = abs(dirAxis) < 1.0e-6
            ? (offsetAxis >= 0.0 ? vec2(-FAR, FAR) : vec2(1.0, 0.0))
            : (dirAxis > 0.0 ? vec2(-offsetAxis / dirAxis, FAR) : vec2(-FAR, -offsetAxis / dirAxis));
    float a = dirAxis * dirAxis - cos2;
    float halfB = dirAxis * offsetAxis - cos2 * dot(dir, offset);
    float c = offsetAxis * offsetAxis - cos2 * dot(offset, offset);
    if (abs(a) < 1.0e-5) {
        return front;
    }
    float disc = halfB * halfB - a * c;
    if (disc < 0.0) {
        return a > 0.0 ? front : vec2(1.0, 0.0);
    }
    float root = sqrt(disc);
    float t0 = (-halfB - root) / a;
    float t1 = (-halfB + root) / a;
    float low = min(t0, t1);
    float high = max(t0, t1);
    vec2 piece;
    if (a < 0.0) {
        if (offsetAxis + 0.5 * (low + high) * dirAxis < 0.0) {
            return vec2(1.0, 0.0);
        }
        piece = vec2(low, high);
    } else {
        piece = offsetAxis + (high + 1.0) * dirAxis >= 0.0 ? vec2(high, FAR) : vec2(-FAR, low);
    }
    return vec2(max(piece.x, front.x), min(piece.y, front.y));
}

// Interleaved gradient noise: a stable per-pixel jitter for the march start. / 交错梯度噪声：稳定的逐像素步进抖动。
float jitter(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

vec3 beam(vec3 start, vec3 dir, float sceneDist) {
    vec2 sphere = sphere_interval(start, dir, LightOrigin, RangeFalloff.x);
    vec2 cone = cone_interval(start, dir, LightOrigin, LightDirection, ConeCos.z);
    float near = max(max(sphere.x, cone.x), 0.0);
    float far = min(min(sphere.y, cone.y), sceneDist);
    if (far <= near) {
        return vec3(0.0);
    }
    float stepLength = (far - near) / float(STEPS);
    float offset = jitter(gl_FragCoord.xy);
    float density = 0.0;
    for (int i = 0; i < STEPS; i++) {
        vec3 pos = start + dir * (near + (float(i) + offset) * stepLength);
        vec3 rel = pos - LightOrigin;
        float dist = max(length(rel), 1.0e-4);
        float weight = flashlight_cone(dot(rel, LightDirection) / dist) * flashlight_distance(dist);
        if (weight <= 0.0) {
            continue;
        }
        if (ShadowEnabled > 0.5) {
            vec2 texel;
            float shadowDist;
            weight *= flashlight_grid_point(pos, texel, shadowDist)
                    ? flashlight_cell_lit(ivec2(floor(texel)), shadowDist) : 0.0;
        }
        density += weight * flashlight_fog_keep(fog_distance(pos, FogShape), FogStart, FogEnd, FogColor);
    }
    float ambient = flashlight_ambient_scale(vec2(RayGrid.y * 0.5));
    return LightColor * ((1.0 - exp(-BeamStrength * density * stepLength)) * ambient);
}

// Soft lens glare when the viewer looks into the light and its origin is not hidden behind scene depth.
// 观察者直视光源且光源原点未被场景深度遮挡时的柔和镜头眩光。
vec3 glare() {
    if (GlareStrength <= 0.0) {
        return vec3(0.0);
    }
    float originDist = max(length(LightOrigin), 1.0e-4);
    float facing = dot(-LightOrigin / originDist, LightDirection);
    float strength = GlareStrength * flashlight_exposed(flashlight_intensity(facing, originDist));
    vec4 clip = ViewProjMat * vec4(LightOrigin, 1.0);
    if (strength <= 0.0 || clip.w <= 0.0) {
        return vec3(0.0);
    }
    vec2 originUv = clip.xy / clip.w * 0.5 + 0.5;
    float radius = length((texCoord - originUv) * vec2(ScreenSize.x / ScreenSize.y, 1.0));
    if (radius >= GLARE_RADIUS) {
        return vec3(0.0);
    }
    float visible = 0.0;
    for (int i = 0; i < 5; i++) {
        vec2 uv = originUv + GLARE_TAPS[i] / ScreenSize;
        if (all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)))) {
            float sceneDist = length(unproject(uv, texture(DepthSampler, uv).r));
            visible += sceneDist + GLARE_DEPTH_SLACK >= originDist ? 0.2 : 0.0;
        }
    }
    float shape = exp(-radius * radius * 6000.0) + 0.3 * exp(-radius * 18.0);
    float keep = flashlight_fog_keep(fog_distance(LightOrigin, FogShape), FogStart, FogEnd, FogColor);
    return LightColor * (strength * visible * shape * keep);
}

void main() {
    float sceneDepth = texture(DepthSampler, texCoord).r;
    vec3 start = unproject(texCoord, 0.0);
    vec3 ray = unproject(texCoord, sceneDepth) - start;
    float sceneDist = length(ray);
    vec3 dir = ray / max(sceneDist, 1.0e-4);
    fragColor = vec4(beam(start, dir, sceneDist) + glare(), 0.0);
}
