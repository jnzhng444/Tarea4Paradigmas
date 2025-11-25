package com.dkj.events;

/**
 * Interfaz base para eventos del juego (Sealed Interface).
 * 
 * Marca comun para todos los eventos que pueden ser emitidos por el motor
 * de juego. Utiliza sealed interface para restringir los tipos de eventos
 * a un conjunto conocido y cerrado.
 * 
 * Eventos permitidos:
 * - StateEvent: Snapshot completo del estado del juego
 * - ScoreEvent: Notificacion de puntos ganados
 * - DeathEvent: Notificacion de muerte de jugador
 * - LevelEvent: Notificacion de cambio de nivel
 * 
 * Patron aplicado: Marker Interface con restriccion de tipo (Sealed - Java 17+)
 */
public sealed interface GameEvent permits StateEvent, ScoreEvent, DeathEvent, LevelEvent {}
