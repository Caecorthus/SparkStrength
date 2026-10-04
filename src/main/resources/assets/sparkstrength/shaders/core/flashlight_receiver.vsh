#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;

// Section-relative vertices plus ChunkOffset (section origin - camera) give camera-relative positions, like vanilla
// terrain, so floats stay precise far from the world origin.
// 区段相对顶点加上 ChunkOffset（区段原点 - 相机）得到相对相机的位置，与原版地形一致，远离世界原点时仍保持浮点精度。
out vec3 worldPos;
out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec3 vertexNormal;

void main() {
    vec3 pos = Position + ChunkOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    worldPos = pos;
    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = Color;
    texCoord0 = UV0;
    vertexNormal = Normal;
}
