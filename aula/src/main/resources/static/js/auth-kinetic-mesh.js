/**
 * Bustraker Hyper-Velocity Cockpit Engine
 * Autopista 3D cinemática infinita nocturna a través de Cartagena.
 * La tarjeta es la cabina de mando (HUD) y toda la pantalla reacciona a la velocidad del vehículo.
 */
(function (global) {
    'use strict';

    function initHyperVelocityEngine() {
        var canvas = document.getElementById('kineticCanvas');
        if (!canvas) {
            canvas = document.createElement('canvas');
            canvas.id = 'kineticCanvas';
            canvas.className = 'kinetic-canvas';
            canvas.setAttribute('aria-hidden', 'true');
            document.body.prepend(canvas);
        }

        var ctx = canvas.getContext('2d');
        if (!ctx) return;

        var width = 0, height = 0;
        var animId = null;
        var speed = 1.0;
        var targetSpeed = 1.0;
        var distance = 0;
        var curveAngle = 0;
        var targetCurve = 0;
        var mouseOffset = { x: 0, y: 0, targetX: 0, targetY: 0 };

        // Parámetros de la carretera 3D
        var FOV = 280;
        var CAMERA_HEIGHT = 160;
        var ROAD_WIDTH = 1200;
        var SEGMENT_LENGTH = 100;
        var NUM_SEGMENTS = 140;

        // Tráfico y partículas de túnel
        var cars = [];
        var stars = [];
        var lightBeams = [];
        var skylineBuildings = [];

        function resize() {
            width = canvas.width = window.innerWidth;
            height = canvas.height = window.innerHeight;
            FOV = Math.max(220, Math.min(380, width * 0.28));
            generateWorld();
        }

        function generateWorld() {
            stars = [];
            for (var i = 0; i < 180; i++) {
                stars.push({
                    x: (Math.random() - 0.5) * width * 2,
                    y: (Math.random() - 0.5) * height * 1.2,
                    z: 200 + Math.random() * 1200,
                    size: 1 + Math.random() * 2,
                    alpha: 0.2 + Math.random() * 0.7
                });
            }

            cars = [];
            // Vehículos en la autopista (estelas de tráfico)
            for (var c = 0; c < 16; c++) {
                var lane = (Math.random() > 0.5 ? 1 : -1) * (180 + Math.random() * 320);
                var isIncoming = lane < 0;
                cars.push({
                    x: lane,
                    z: 500 + c * 550,
                    speed: isIncoming ? -1.8 : 0.6,
                    color: isIncoming ? '#ff3366' : '#00f5d4',
                    trailLen: 120 + Math.random() * 100
                });
            }

            // Haces de luz de alta velocidad (Warp Streaks)
            lightBeams = [];
            for (var b = 0; b < 45; b++) {
                lightBeams.push({
                    x: (Math.random() - 0.5) * 2200,
                    y: -400 + Math.random() * 800,
                    z: 200 + Math.random() * 3000,
                    len: 200 + Math.random() * 400,
                    color: Math.random() > 0.5 ? '#00f5d4' : (Math.random() > 0.5 ? '#0ea5e9' : '#ec4899')
                });
            }

            // Siluetas del horizonte de Cartagena (Torre del reloj, rascacielos de Bocagrande)
            skylineBuildings = [];
            var numBld = 36;
            for (var s = 0; s < numBld; s++) {
                skylineBuildings.push({
                    x: (s / numBld) * 2 - 1, // de -1 a 1
                    width: 0.03 + Math.random() * 0.05,
                    height: 40 + Math.random() * 120,
                    hasSpire: Math.random() > 0.7,
                    beacon: Math.random() > 0.6
                });
            }
        }

        function project(x, y, z, cx, cy) {
            if (z <= 1) z = 1;
            var scale = FOV / z;
            return {
                x: cx + x * scale,
                y: cy + (y + CAMERA_HEIGHT) * scale,
                scale: scale
            };
        }

        // ===================== CICLO DE ACTUALIZACIÓN =====================
        function update() {
            // Suavizado de velocidad e inclinación
            speed += (targetSpeed - speed) * 0.08;
            distance += speed * 18;

            mouseOffset.x += (mouseOffset.targetX - mouseOffset.x) * 0.06;
            mouseOffset.y += (mouseOffset.targetY - mouseOffset.y) * 0.06;

            // Curvas suaves periódicas en la autopista de Cartagena
            targetCurve = Math.sin(distance * 0.0006) * 280;
            curveAngle += (targetCurve - curveAngle) * 0.04;

            // Transmitir telemetría de velocidad y coordenadas al DOM
            var kmh = Math.round(75 * speed);
            var telemetrySpeedEl = document.getElementById('telemetryKmh');
            if (telemetrySpeedEl) {
                telemetrySpeedEl.textContent = kmh;
            }

            // Mover vehículos
            for (var i = 0; i < cars.length; i++) {
                var car = cars[i];
                car.z -= (speed + car.speed) * 16;
                if (car.z < 60) {
                    car.z += 8000;
                    car.x = (Math.random() > 0.5 ? 1 : -1) * (180 + Math.random() * 320);
                } else if (car.z > 8200) {
                    car.z -= 8000;
                }
            }

            // Mover haces de luz
            for (var j = 0; j < lightBeams.length; j++) {
                var beam = lightBeams[j];
                beam.z -= speed * 42;
                if (beam.z < 50) {
                    beam.z += 3500;
                    beam.x = (Math.random() - 0.5) * 2400;
                    beam.y = -350 + Math.random() * 700;
                }
            }
        }

        // ===================== DIBUJADO 3D =====================
        function draw() {
            ctx.clearRect(0, 0, width, height);

            var cx = width * 0.5 + mouseOffset.x * 0.2;
            var cy = height * 0.44 + mouseOffset.y * 0.15;

            // 1. CIELO NOCTURNO CON GRADIENTE CIBERNÉTICO
            var skyGrad = ctx.createRadialGradient(cx, cy - 80, 50, cx, cy - 80, Math.max(width, height) * 0.85);
            skyGrad.addColorStop(0, '#0c1d36');
            skyGrad.addColorStop(0.35, '#040d1c');
            skyGrad.addColorStop(0.75, '#02050c');
            skyGrad.addColorStop(1, '#000206');
            ctx.fillStyle = skyGrad;
            ctx.fillRect(0, 0, width, height);

            // 2. ESTRELLAS EN PERSPECTIVA
            for (var s = 0; s < stars.length; s++) {
                var star = stars[s];
                var starZ = star.z - (distance * 0.05) % star.z;
                var sp = project(star.x - curveAngle * 0.4, star.y - 180, starZ, cx, cy);
                if (sp.y < cy) {
                    ctx.fillStyle = 'rgba(255, 255, 255, ' + star.alpha + ')';
                    ctx.beginPath();
                    ctx.arc(sp.x, sp.y, Math.max(0.6, star.size * sp.scale * 1.5), 0, Math.PI * 2);
                    ctx.fill();
                }
            }

            // 3. SILUETA DEL HORIZONTE DE CARTAGENA (Skyline)
            var horizonY = cy;
            ctx.fillStyle = '#020914';
            for (var b = 0; b < skylineBuildings.length; b++) {
                var bld = skylineBuildings[b];
                var bx = cx + bld.x * (width * 0.7) - curveAngle * 0.15;
                var bw = bld.width * width * 0.8;
                var bh = bld.height;

                // Edificio
                ctx.fillRect(bx - bw / 2, horizonY - bh, bw, bh);

                // Luz baliza roja de rascacielos
                if (bld.beacon && Math.sin(distance * 0.05 + b) > 0) {
                    ctx.fillStyle = '#ef4444';
                    ctx.beginPath();
                    ctx.arc(bx, horizonY - bh - 2, 1.8, 0, Math.PI * 2);
                    ctx.fill();
                    ctx.fillStyle = '#020914';
                }

                // Ventanas iluminadas cian tenue
                ctx.fillStyle = 'rgba(0, 245, 212, 0.08)';
                for (var wY = horizonY - bh + 12; wY < horizonY - 10; wY += 14) {
                    for (var wX = bx - bw / 2 + 5; wX < bx + bw / 2 - 5; wX += 8) {
                        if (Math.sin(wX * 12 + wY) > 0.2) {
                            ctx.fillRect(wX, wY, 3, 5);
                        }
                    }
                }
                ctx.fillStyle = '#020914';
            }

            // Resplandor del horizonte
            var horizonGlow = ctx.createLinearGradient(0, horizonY - 40, 0, horizonY + 20);
            horizonGlow.addColorStop(0, 'rgba(0, 245, 212, 0)');
            horizonGlow.addColorStop(0.5, 'rgba(0, 245, 212, 0.18)');
            horizonGlow.addColorStop(1, 'rgba(0, 0, 0, 0.85)');
            ctx.fillStyle = horizonGlow;
            ctx.fillRect(0, horizonY - 40, width, 60);

            // 4. AUTOPISTA INFINITA (Segmentos 3D de atrás hacia adelante)
            var currentSegmentOffset = (distance % SEGMENT_LENGTH);

            for (var n = NUM_SEGMENTS - 1; n >= 0; n--) {
                var z1 = n * SEGMENT_LENGTH - currentSegmentOffset;
                var z2 = (n + 1) * SEGMENT_LENGTH - currentSegmentOffset;
                if (z1 <= 20) continue;

                var curveZ1 = Math.sin((distance + z1) * 0.0006) * curveAngle;
                var curveZ2 = Math.sin((distance + z2) * 0.0006) * curveAngle;

                var p1L = project(-ROAD_WIDTH / 2 + curveZ1, 0, z1, cx, cy);
                var p1R = project(ROAD_WIDTH / 2 + curveZ1, 0, z1, cx, cy);
                var p2L = project(-ROAD_WIDTH / 2 + curveZ2, 0, z2, cx, cy);
                var p2R = project(ROAD_WIDTH / 2 + curveZ2, 0, z2, cx, cy);

                // Color del asfalto reflectivo
                var isDark = Math.floor((distance + z1) / SEGMENT_LENGTH) % 2 === 0;
                ctx.fillStyle = isDark ? '#040b17' : '#030812';

                ctx.beginPath();
                ctx.moveTo(p1L.x, p1L.y);
                ctx.lineTo(p1R.x, p1R.y);
                ctx.lineTo(p2R.x, p2R.y);
                ctx.lineTo(p2L.x, p2L.y);
                ctx.closePath();
                ctx.fill();

                // BARRERAS LATERALES DE NEÓN (Cian brillante)
                var railAlpha = Math.max(0, 1 - z1 / 4500);
                ctx.strokeStyle = 'rgba(0, 245, 212, ' + (railAlpha * 0.85) + ')';
                ctx.lineWidth = Math.max(1.5, 4 * p1L.scale);
                ctx.beginPath();
                ctx.moveTo(p1L.x, p1L.y);
                ctx.lineTo(p2L.x, p2L.y);
                ctx.moveTo(p1R.x, p1R.y);
                ctx.lineTo(p2R.x, p2R.y);
                ctx.stroke();

                // LÍNEAS CENTRALES DE CARRIL (Divisores discontinuos en neón ámbar/cian)
                if (isDark) {
                    // Carril izquierdo
                    var laneLeft1 = project(-ROAD_WIDTH * 0.22 + curveZ1, 0, z1, cx, cy);
                    var laneLeft2 = project(-ROAD_WIDTH * 0.22 + curveZ2, 0, z2, cx, cy);
                    // Carril derecho
                    var laneRight1 = project(ROAD_WIDTH * 0.22 + curveZ1, 0, z1, cx, cy);
                    var laneRight2 = project(ROAD_WIDTH * 0.22 + curveZ2, 0, z2, cx, cy);

                    ctx.strokeStyle = 'rgba(56, 189, 248, ' + (railAlpha * 0.7) + ')';
                    ctx.lineWidth = Math.max(1, 2.5 * p1L.scale);
                    ctx.beginPath();
                    ctx.moveTo(laneLeft1.x, laneLeft1.y);
                    ctx.lineTo(laneLeft2.x, laneLeft2.y);
                    ctx.moveTo(laneRight1.x, laneRight1.y);
                    ctx.lineTo(laneRight2.x, laneRight2.y);
                    ctx.stroke();

                    // Doble línea central amarilla
                    var center1 = project(curveZ1, 0, z1, cx, cy);
                    var center2 = project(curveZ2, 0, z2, cx, cy);
                    ctx.strokeStyle = 'rgba(251, 191, 36, ' + (railAlpha * 0.9) + ')';
                    ctx.lineWidth = Math.max(1, 3 * p1L.scale);
                    ctx.beginPath();
                    ctx.moveTo(center1.x, center1.y);
                    ctx.lineTo(center2.x, center2.y);
                    ctx.stroke();
                }
            }

            // 5. TRÁFICO (Vehículos con estelas de luz estiradas)
            for (var c = 0; c < cars.length; c++) {
                var car = cars[c];
                var carCurve = Math.sin((distance + car.z) * 0.0006) * curveAngle;
                var cpHead = project(car.x + carCurve, 0, car.z, cx, cy);
                var cpTail = project(car.x + carCurve, 0, car.z + car.trailLen * speed, cx, cy);

                if (cpHead.y > horizonY && cpHead.y < height + 80) {
                    var carAlpha = Math.max(0, 1 - car.z / 6500);
                    var strokeW = Math.max(2, 8 * cpHead.scale);

                    var carGrad = ctx.createLinearGradient(cpTail.x, cpTail.y, cpHead.x, cpHead.y);
                    carGrad.addColorStop(0, 'rgba(0, 0, 0, 0)');
                    carGrad.addColorStop(0.3, car.color);
                    carGrad.addColorStop(1, '#ffffff');

                    ctx.strokeStyle = carGrad;
                    ctx.lineWidth = strokeW;
                    ctx.lineCap = 'round';
                    ctx.beginPath();
                    ctx.moveTo(cpTail.x, cpTail.y);
                    ctx.lineTo(cpHead.x, cpHead.y);
                    ctx.stroke();

                    // Destello de faro
                    ctx.fillStyle = '#ffffff';
                    ctx.shadowColor = car.color;
                    ctx.shadowBlur = 16 * cpHead.scale;
                    ctx.beginPath();
                    ctx.arc(cpHead.x, cpHead.y, strokeW * 0.8, 0, Math.PI * 2);
                    ctx.fill();
                    ctx.shadowBlur = 0; // reset
                }
            }

            // 6. RAYOS DE HIPER-ESPACIO (Warp Streaks volando hacia la cámara)
            for (var w = 0; w < lightBeams.length; w++) {
                var b = lightBeams[w];
                var bp1 = project(b.x - curveAngle * 0.3, b.y, b.z, cx, cy);
                var bp2 = project(b.x - curveAngle * 0.3, b.y, b.z + b.len * (0.8 + speed * 0.4), cx, cy);

                if (bp1.y > 0 && bp1.y < height && bp1.x > 0 && bp1.x < width) {
                    var beamAlpha = Math.max(0, 1 - b.z / 3200) * (speed > 1.2 ? 0.9 : 0.45);
                    ctx.strokeStyle = b.color === '#00f5d4'
                        ? 'rgba(0, 245, 212, ' + beamAlpha + ')'
                        : 'rgba(56, 189, 248, ' + beamAlpha + ')';
                    ctx.lineWidth = Math.max(1, 2.8 * bp1.scale);
                    ctx.beginPath();
                    ctx.moveTo(bp2.x, bp2.y);
                    ctx.lineTo(bp1.x, bp1.y);
                    ctx.stroke();
                }
            }

            // 7. EFECTO DE CRISTAL DE PARABRISAS (Cockpit Vignette & Glare)
            var vignette = ctx.createRadialGradient(cx, cy, height * 0.35, cx, cy, width * 0.85);
            vignette.addColorStop(0, 'rgba(0, 0, 0, 0)');
            vignette.addColorStop(0.7, 'rgba(2, 6, 17, 0.45)');
            vignette.addColorStop(1, 'rgba(1, 3, 9, 0.88)');
            ctx.fillStyle = vignette;
            ctx.fillRect(0, 0, width, height);

            // Reflejo diagonal anamórfico sutil en el parabrisas
            var glareAngle = (distance * 0.002) % (Math.PI * 2);
            if (Math.sin(glareAngle) > 0.8) {
                var glareGrad = ctx.createLinearGradient(0, 0, width, height);
                glareGrad.addColorStop(0, 'rgba(0, 245, 212, 0)');
                glareGrad.addColorStop(0.5, 'rgba(0, 245, 212, 0.03)');
                glareGrad.addColorStop(1, 'rgba(0, 245, 212, 0)');
                ctx.fillStyle = glareGrad;
                ctx.fillRect(0, 0, width, height);
            }
        }

        function loop() {
            update();
            draw();
            animId = requestAnimationFrame(loop);
        }

        // ===================== CONTROL DE ACELERACIÓN Y COCKPIT =====================
        function initInteractions() {
            var submitButtons = document.querySelectorAll('button[type="submit"]');
            var inputs = document.querySelectorAll('.form-control');
            var card = document.querySelector('.card_animate-slideUp');

            // Al interactuar con el botón de ingreso o submit: ¡HIPER-ACELERACIÓN!
            submitButtons.forEach(function (btn) {
                btn.addEventListener('mouseenter', function () {
                    targetSpeed = 2.4; // BOOST x 2.4
                    document.body.classList.add('hyper-boost-active');
                });
                btn.addEventListener('mouseleave', function () {
                    targetSpeed = 1.0;
                    document.body.classList.remove('hyper-boost-active');
                });
                btn.addEventListener('mousedown', function () {
                    targetSpeed = 3.2; // WARP IGNITION
                });
            });

            // Al escribir en los inputs: aceleración crucero
            inputs.forEach(function (input) {
                input.addEventListener('focus', function () {
                    targetSpeed = 1.35;
                });
                input.addEventListener('blur', function () {
                    targetSpeed = 1.0;
                });
            });

            // Giro giroscópico con el mouse
            window.addEventListener('mousemove', function (e) {
                mouseOffset.targetX = (e.clientX - width / 2);
                mouseOffset.targetY = (e.clientY - height / 2);

                if (card && window.innerWidth >= 1024) {
                    var dx = (e.clientX - width / 2) / (width / 2);
                    var dy = (e.clientY - height / 2) / (height / 2);
                    card.style.transform = 'perspective(1400px) rotateX(' + (-dy * 6.5).toFixed(2) + 'deg) rotateY(' + (dx * 6.5).toFixed(2) + 'deg) translateZ(10px)';
                    card.style.setProperty('--spotlight-x', ((e.clientX - card.offsetLeft) / card.offsetWidth * 100).toFixed(1) + '%');
                    card.style.setProperty('--spotlight-y', ((e.clientY - card.offsetTop) / card.offsetHeight * 100).toFixed(1) + '%');
                }
            });

            window.addEventListener('mouseleave', function () {
                mouseOffset.targetX = 0;
                mouseOffset.targetY = 0;
                if (card) {
                    card.style.transform = 'perspective(1400px) rotateX(0deg) rotateY(0deg) translateZ(0)';
                }
            });
        }

        window.addEventListener('resize', resize);

        document.addEventListener('visibilitychange', function () {
            if (document.hidden) {
                if (animId) cancelAnimationFrame(animId);
            } else {
                animId = requestAnimationFrame(loop);
            }
        });

        resize();
        initInteractions();
        animId = requestAnimationFrame(loop);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initHyperVelocityEngine);
    } else {
        initHyperVelocityEngine();
    }
})(window);
