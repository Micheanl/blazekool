${GLSL_VERSION}
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
${FOG_UNIFORMS}
${FOG_FUNCTIONS}
uniform sampler2D Sampler0;
in vec2 blazekoolTexCoord0;
in vec4 blazekoolVertexColor;
in vec3 blazekoolNormal;
${LIGHTING_VARYINGS}
${FOG_VARYINGS}
out vec4 fragColor;
${COLORSPACE_FUNCTIONS}
void main() {
    vec4 color = texture(Sampler0, blazekoolTexCoord0) * blazekoolVertexColor;
${ALPHA_BODY}
${OPAQUE_ALPHA_BODY}
${LIGHTING_BODY}
${COLORSPACE_BODY}
${PREMULTIPLIED_ALPHA_BODY}
    color *= ColorModulator;
${FOG_BODY}
    fragColor = color;
}
