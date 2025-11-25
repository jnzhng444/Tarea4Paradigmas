/**
 * @file types.h
 * @brief Estructuras de datos del cliente Donkey Kong Jr
 * 
 * Define todas las estructuras de datos utilizadas para representar el estado
 * del juego, entidades y nivel. Estas estructuras se sincronizan con el servidor
 * mediante el protocolo de red.
 * 
 * Organizacion:
 * - Entidades: PlayerState, RedCroc, BlueCroc, Fruit
 * - Nivel/Mapa: Platform, Liana, Level
 * - Mundo: World (snapshot completo del estado del juego)
 */

#ifndef TYPES_H
#define TYPES_H

#include <stdbool.h>
#include "raylib.h"

// ============ ENTIDADES DEL JUEGO ============

/**
 * Estado de un jugador en el juego.
 * 
 * Contiene toda la informacion necesaria para representar y simular un jugador,
 * incluyendo posicion, velocidad, estado de contacto, vidas, puntaje y flags visuales.
 * 
 * Campos:
 * - id: Identificador unico del jugador (PlayerId del servidor)
 * - pos: Posicion actual en pixeles (x, y)
 * - vel: Velocidad actual en pixeles/frame (vx, vy)
 * - onGround: true si esta sobre una plataforma
 * - onLiana: true si esta agarrado a una liana
 * - lianaIndex: Indice de la liana actual (-1 si no esta en ninguna)
 * - lives: Vidas restantes
 * - score: Puntos acumulados
 * - facingRight: true si mira a la derecha, false si mira a la izquierda
 * - respawned: true si acaba de reaparecer tras morir (para efecto visual)
 */
typedef struct {
    char id[64];
    Vector2 pos;
    Vector2 vel;
    bool onGround;
    bool onLiana;
    int lianaIndex;
    int lives;
    int score;
    bool facingRight;
    bool respawned;     // 🔹 nuevo: para feedback de colision con cocodrilos
} PlayerState;

/**
 * Cocodrilo rojo - Enemigo que patrulla lianas verticalmente.
 * 
 * Representa un enemigo que se mueve hacia arriba y abajo en una liana,
 * rebotando entre limites verticales. Es sincronizado desde el servidor.
 * 
 * Campos:
 * - pos: Posicion actual en pixeles (x, y)
 * - lianaIndex: Indice de la liana donde patrulla
 * - speed: Velocidad de movimiento vertical
 * - minH, maxH: Limites verticales de patrullaje (altura logica)
 * - goingUp: true si se mueve hacia arriba, false si baja
 */
typedef struct {
    Vector2 pos;
    int lianaIndex;
    float speed;
    float minH, maxH;
    bool goingUp;
} RedCroc;

/**
 * Cocodrilo azul - Enemigo con comportamiento de dos fases.
 * 
 * Representa un enemigo que primero camina en plataformas y luego desciende
 * por lianas. Comportamiento mas complejo que el cocodrilo rojo.
 * 
 * Campos:
 * - pos: Posicion actual en pixeles (x, y)
 * - lianaIndex: Indice de la liana si esta descendiendo
 * - speed: Velocidad de movimiento
 * - active: true si esta activo en el juego
 * - isWalking: true cuando camina en plataforma, false cuando desciende
 */
typedef struct {
    Vector2 pos;
    int lianaIndex;
    float speed;
    bool active;
    bool isWalking;  // true cuando está caminando en plataforma, false cuando desciende
} BlueCroc;

/**
 * Fruta coleccionable que otorga puntos.
 * 
 * Representa un objeto que el jugador puede recoger para ganar puntos.
 * Puede ser de diferentes tipos visuales (bananas, naranjas, fresa).
 * 
 * Campos:
 * - pos: Posicion en pixeles (x, y)
 * - points: Valor en puntos de esta fruta
 * - collected: true si ya fue recolectada por el jugador
 * - fruitType: Tipo visual (0=bananas, 1=oranges, 2=strawberry)
 */
typedef struct {
    Vector2 pos;
    int points;
    bool collected;
    int fruitType;  // 0=bananas, 1=oranges, 2=strawberry
} Fruit;

// ============ NIVEL/MAPA ============

/**
 * Plataforma horizontal en el nivel.
 * 
 * Representa una superficie solida rectangular donde el jugador puede caminar.
 * Se define mediante un rectangulo (x, y, ancho, alto).
 * 
 * Campo:
 * - rect: Rectangulo que define posicion y dimensiones de la plataforma
 */
typedef struct {
    Rectangle rect;
} Platform;

/**
 * Liana vertical por la que el jugador puede trepar.
 * 
 * Representa una estructura vertical con posicion horizontal fija y limites
 * verticales superior e inferior. El jugador puede agarrarse y subir/bajar.
 * 
 * Campos:
 * - x: Posicion horizontal fija de la liana (pixeles)
 * - topY: Limite superior de la liana (pixeles)
 * - bottomY: Limite inferior de la liana (pixeles)
 */
typedef struct {
    float x;
    float topY, bottomY;
} Liana;

/**
 * Configuracion completa del nivel de juego.
 * 
 * Contiene toda la geometria estatica del nivel: plataformas, lianas,
 * posiciones de NPCs (DK, Mario) y objetivos (llave, plataforma de victoria).
 * Esta estructura se inicializa una vez al inicio del juego.
 * 
 * Campos:
 * - platforms[]: Array de plataformas del nivel
 * - platformCount: Numero de plataformas activas
 * - lianas[]: Array de lianas (6 principales + 1 mini)
 * - lianaCount: Numero de lianas activas
 * - dkPosition: Posicion de Donkey Kong (NPC)
 * - marioPosition: Posicion de Mario (NPC)
 * - keyPosition: Posicion de la llave (objetivo)
 * - winPlatform: Rectangulo de la miniplataforma de victoria (arriba de Mario)
 * - hasKey: true si el jugador ya recogio la llave
 */
typedef struct {
    Platform platforms[16];
    int platformCount;
    Liana lianas[7];  // 6 lianas principales + 1 mini liana
    int lianaCount;
    Vector2 dkPosition;
    Vector2 marioPosition;
    Vector2 keyPosition;
    Rectangle winPlatform;  // Miniplataforma arriba de Mario
    bool hasKey;  // Si el jugador ya recogió la llave
} Level;

// ============ MUNDO (snapshot del servidor) ============

/**
 * Snapshot completo del estado del juego.
 * 
 * Representa el estado completo del mundo de juego en un instante dado.
 * Se actualiza periodicamente con datos recibidos del servidor via mensajes STATE.
 * Contiene todos los jugadores, enemigos y frutas activos.
 * 
 * Capacidades maximas definidas en constants.h:
 * - MAX_REDS = 32
 * - MAX_BLUES = 32
 * - MAX_FRUITS = 32
 * 
 * Campos:
 * - players[]: Array de jugadores activos (max 2)
 * - playerCount: Numero de jugadores actualmente en juego
 * - reds[]: Array de cocodrilos rojos activos
 * - redCount: Numero de cocodrilos rojos
 * - blues[]: Array de cocodrilos azules activos
 * - blueCount: Numero de cocodrilos azules
 * - fruits[]: Array de frutas activas
 * - fruitCount: Numero de frutas
 */
typedef struct {
    PlayerState players[2];
    int playerCount;
    RedCroc reds[32];
    int redCount;
    BlueCroc blues[32];
    int blueCount;
    Fruit fruits[32];
    int fruitCount;
} World;

#endif
