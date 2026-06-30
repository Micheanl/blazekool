${GLSL_VERSION}
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};
${LIGHTING_UNIFORMS}
${FOG_UNIFORMS}
${LIGHTING_FUNCTIONS}
${FOG_FUNCTIONS}
in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;
out vec2 blazekoolTexCoord0;
out vec4 blazekoolVertexColor;
out vec3 blazekoolNormal;
${LIGHTING_VARYINGS}
${FOG_VARYINGS}
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position + ModelOffset, 1.0);
    blazekoolTexCoord0 = UV0;
    blazekoolVertexColor = Color;
    blazekoolNormal = Normal;
${LIGHTING_BODY}
${FOG_BODY}
}
