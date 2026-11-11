precision mediump float;

uniform vec2 u_resolution;
uniform float u_time;
uniform vec3 u_color;
uniform sampler2D u_fontTexture;
uniform float u_cols;
uniform float u_charCount;

void main() {
    vec2 st = gl_FragCoord.xy / u_resolution.xy;
    st.y = 1.0 - st.y; // Инверсия Y под OpenGL

    float aspect = u_resolution.x / u_resolution.y;
    float rows = floor(u_cols / aspect);
    vec2 grid = vec2(u_cols, rows);

    // Координаты ячейки сетки и внутри символа [0..1]
    vec2 cell = floor(st * grid);
    vec2 cellUv = fract(st * grid);

    // Центрированные координаты для 3D-математики (-1..1)
    vec2 p = (cell - grid * 0.5) / (grid.y * 0.5);

    // РЕНДЕР СЦЕНЫ: Пример динамической Чёрной Дыры на GPU
    float r = length(p);
    float a = atan(p.y, p.x);
    float disk = sin(1.0 / (r + 0.1) * 8.0 - u_time * 3.0 + a * 2.0);
    float lum = smoothstep(0.1, 0.9, disk) * exp(-r * 0.8);

    // Защита фона
    if (r < 0.2) lum = 0.0; // Тень горизонта событий

    // Выбор символа из текстурного атласа
    float charIdx = floor(clamp(lum, 0.0, 0.99) * u_charCount);
    vec2 fontUv = vec2((cellUv.x + charIdx) / u_charCount, cellUv.y);

    // Сэмплирование текстуры
    float charAlpha = texture2D(u_fontTexture, fontUv).a;
    gl_FragColor = vec4(u_color * charAlpha * lum, 1.0);
}
