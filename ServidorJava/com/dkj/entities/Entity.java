package com.dkj.entities;

import com.dkj.model.Position;


/**
 * Interfaz base para todas las entidades del juego.
 * 
 * Define el contrato comun para cualquier entidad que existe en el espacio del juego
 * y tiene una posicion. Utiliza sealed interface para restringir las implementaciones
 * a las tres entidades conocidas del dominio.
 * 
 * Implementaciones permitidas:
 * - CrocodileRed: Cocodrilos rojos que suben y bajan por lianas
 * - CrocodileBlue: Cocodrilos azules que caminan en plataformas y descienden
 * - Fruit: Frutas coleccionables que otorgan puntos
 * 
 * Patron aplicado: Marker Interface con restriccion de tipo (Sealed Interface - Java 17+)
 */
public sealed interface Entity permits CrocodileRed, CrocodileBlue, Fruit {
    /**
     * Obtiene la posicion actual de la entidad en el espacio del juego.
     * 
     * @return Position que indica la liana y altura donde se encuentra la entidad
     */
    Position position();
}
