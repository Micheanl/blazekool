    vec3 normal = normalize(Normal);
    vec2 light = minecraft_compute_light(Light0_Direction, Light1_Direction, normal);
    vec2 lightValue = max(vec2(0.0), light);
    blazekoolLight = min(1.0, (lightValue.x + lightValue.y) * 0.6 + 0.4);
