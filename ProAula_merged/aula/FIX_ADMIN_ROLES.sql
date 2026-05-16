-- Script para corregir y normalizar roles de administrador en la base de datos
-- Este script asegura que todos los roles estén normalizados con el prefijo ROLE_

-- 1. Normalizador de roles ADMIN
-- Convierte "ADMIN" a "ROLE_ADMIN"
UPDATE usuario 
SET role = 'ROLE_ADMIN' 
WHERE role = 'ADMIN' OR role = 'admin';

-- 2. Normalizador de roles USER
-- Convierte "USER" a "ROLE_USER"
UPDATE usuario 
SET role = 'ROLE_USER' 
WHERE role = 'USER' OR role = 'user';

-- 3. Convertir cualquier rol sin prefijo ROLE_
-- Para usuarios con roles personalizados
UPDATE usuario 
SET role = CONCAT('ROLE_', role) 
WHERE role IS NOT NULL 
AND role != '' 
AND role NOT LIKE 'ROLE_%';

-- 4. Asignar rol por defecto si es NULL
UPDATE usuario 
SET role = 'ROLE_USER' 
WHERE role IS NULL OR role = '';

-- Verificar cambios realizados
SELECT id, username, nombres, role, email FROM usuario ORDER BY id;

-- Resumen de usuarios por rol
SELECT role, COUNT(*) as cantidad FROM usuario GROUP BY role;
