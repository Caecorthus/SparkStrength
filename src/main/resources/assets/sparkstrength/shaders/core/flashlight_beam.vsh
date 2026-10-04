#version 150

in vec3 Position;

out vec2 texCoord;

// Fullscreen quad already in clip space; no matrices needed. / 全屏四边形已处于裁剪空间，无需矩阵。
void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = Position.xy * 0.5 + 0.5;
}
